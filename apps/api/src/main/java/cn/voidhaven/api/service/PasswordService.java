package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.ActivationToken;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ActivationTokenRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.support.SecureTokens;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PasswordService {
    public record IssuedReset(String accountId, String loginId, String resetToken, Instant expiresAt) { }
    private final AccountRepository accounts;
    private final ActivationTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PasswordService(AccountRepository accounts, ActivationTokenRepository tokens, PasswordEncoder passwords,
                           JdbcTemplate jdbc, AuditService audit) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void change(String loginId, String currentPassword, String newPassword, String requestId) {
        Account account = accounts.findByLoginId(loginId).flatMap(value -> accounts.findByIdForUpdate(value.getId()))
                .filter(Account::isActive).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号不可用。"));
        if (!passwords.matches(currentPassword, account.getPasswordHash())) throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "当前密码不正确。");
        requireDifferentAndValid(currentPassword, newPassword);
        account.changePassword(passwords.encode(newPassword));
        deleteSessions(loginId);
        String role = account.hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : account.hasRole("ADMIN") ? "ADMIN" : "PARTNER";
        audit.record(account.getId(), role, "PASSWORD_CHANGED", "ACCOUNT", account.getId(), "{}", "{\"sessionsRevoked\":true}", null, requestId);
    }

    @Transactional
    public IssuedReset issueReset(String accountId, String reason, String actorId, String role, String requestId) {
        return issueReset(accountId, "PASSWORD_RESET", reason, actorId, role, requestId, "PASSWORD_RESET_ISSUED");
    }

    @Transactional
    public IssuedReset issueSelfServiceReset(String accountId, String requestId) {
        return issueReset(accountId, "PASSWORD_RESET_SELF_SERVICE", "已验证恢复邮箱", null, "ANONYMOUS", requestId,
                "PASSWORD_RESET_SELF_SERVICE_ISSUED");
    }

    private IssuedReset issueReset(String accountId, String tokenType, String reason, String actorId, String role, String requestId,
                                   String auditAction) {
        Account account = accounts.findByIdForUpdate(accountId).filter(Account::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_RESETTABLE", "只有正常账号可以签发密码重置凭据。"));
        Instant now = Instant.now();
        tokens.invalidateUnusedByType(accountId, tokenType, now);
        String raw = SecureTokens.randomUrlToken();
        Instant expiresAt = now.plus(30, ChronoUnit.MINUTES);
        tokens.save(new ActivationToken(Ids.ulid(), accountId, tokenType, SecureTokens.sha256(raw), expiresAt));
        audit.record(actorId, role, auditAction, "ACCOUNT", accountId, "{}", "{\"expiresInMinutes\":30}", reason, requestId);
        return new IssuedReset(accountId, account.getLoginId(), raw, expiresAt);
    }

    @Transactional
    public String consumeReset(String rawToken, String newPassword) {
        ActivationToken token = tokens.findByTokenHashForUpdate(SecureTokens.sha256(rawToken)).orElseThrow(this::invalidReset);
        Instant now = Instant.now();
        if (!("PASSWORD_RESET".equals(token.getTokenType()) || "PASSWORD_RESET_SELF_SERVICE".equals(token.getTokenType()))
                || !token.isUsableAt(now)) throw invalidReset();
        Account account = accounts.findByIdForUpdate(token.getAccountId()).filter(Account::isActive).orElseThrow(this::invalidReset);
        if (passwords.matches(newPassword, account.getPasswordHash())) throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同。");
        account.changePassword(passwords.encode(newPassword));
        token.consume(now);
        tokens.invalidateUnusedByTypeExcept(account.getId(), "PASSWORD_RESET", token.getId(), now);
        tokens.invalidateUnusedByTypeExcept(account.getId(), "PASSWORD_RESET_SELF_SERVICE", token.getId(), now);
        deleteSessions(account.getLoginId());
        return account.getLoginId();
    }

    private void deleteSessions(String loginId) { jdbc.update("delete from SPRING_SESSION where PRINCIPAL_NAME = ?", loginId); }
    private void requireDifferentAndValid(String current, String next) {
        if (next == null || next.length() < 12 || next.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_FAILED", "密码长度必须为 12 到 128 个字符。");
        if (next.equals(current)) throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同。");
    }
    private ApiException invalidReset() { return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_INVALID", "密码重置凭据无效、已使用或已过期。"); }
}
