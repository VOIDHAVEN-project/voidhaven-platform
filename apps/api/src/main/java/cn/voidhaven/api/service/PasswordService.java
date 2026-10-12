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
    private SecurityRevocationService revocations;
    private CredentialSecurityService credentialSecurity;

    public PasswordService(AccountRepository accounts, ActivationTokenRepository tokens, PasswordEncoder passwords,
                           JdbcTemplate jdbc, AuditService audit) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.audit = audit;
        this.revocations = new SecurityRevocationService(tokens, jdbc);
        this.credentialSecurity = new CredentialSecurityService(accounts);
    }

    @org.springframework.beans.factory.annotation.Autowired
    void security(SecurityRevocationService revocations, CredentialSecurityService credentials) { this.revocations = revocations; this.credentialSecurity = credentials; }

    @Transactional
    public void change(String loginId, String currentPassword, String newPassword, String requestId) {
        Account account = accounts.findByLoginId(loginId).flatMap(value -> accounts.findByIdForUpdate(value.getId()))
                .filter(Account::isActive).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号不可用。"));
        if (!passwords.matches(currentPassword, account.getPasswordHash())) throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "当前密码不正确。");
        requireDifferentAndValid(currentPassword, newPassword);
        account.changePassword(passwords.encode(newPassword));
        revocations.revoke(account);
        String role = account.hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : account.hasRole("ADMIN") ? "ADMIN" : "PARTNER";
        audit.record(account.getId(), role, "PASSWORD_CHANGED", "ACCOUNT", account.getId(), "{}", "{\"sessionsRevoked\":true}", null, requestId);
    }

    @Transactional
    public IssuedReset issueReset(String accountId, String reason, String actorId, String role, String requestId) {
        return issueReset(accountId, "PASSWORD_RESET", reason, actorId, role, requestId, "PASSWORD_RESET_ISSUED");
    }

    private IssuedReset issueReset(String accountId, String tokenType, String reason, String actorId, String role, String requestId,
                                   String auditAction) {
        Account account = accounts.findByIdForUpdate(accountId).filter(Account::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_RESETTABLE", "只有正常账号可以签发密码重置凭据。"));
        if (account.hasRole("SUPER_ADMIN")) throw new ApiException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_WEB_RECOVERY_FORBIDDEN", "超级管理员仅允许通过受控运维入口恢复。" );
        Instant now = Instant.now();
        tokens.invalidateUnusedByType(accountId, tokenType, now);
        String raw = SecureTokens.randomUrlToken();
        Instant expiresAt = now.plus(30, ChronoUnit.MINUTES);
        ActivationToken token = new ActivationToken(Ids.ulid(), accountId, tokenType, SecureTokens.sha256(raw), expiresAt);
        credentialSecurity.bind(token, account);
        tokens.save(token);
        audit.record(actorId, role, auditAction, "ACCOUNT", accountId, "{}", "{\"expiresInMinutes\":30}", reason, requestId);
        return new IssuedReset(accountId, account.getLoginId(), raw, expiresAt);
    }

    @Transactional
    public String consumeReset(String rawToken, String newPassword) { return consumeReset(rawToken, newPassword, Ids.ulid()); }

    @Transactional
    public String consumeReset(String rawToken, String newPassword, String requestId) {
        ActivationToken preview = tokens.findByTokenHash(SecureTokens.sha256(rawToken)).orElseThrow(this::invalidReset);
        Account account = credentialSecurity.lockTarget(preview);
        ActivationToken token = tokens.findByTokenHashForUpdate(SecureTokens.sha256(rawToken)).orElseThrow(this::invalidReset);
        Instant now = Instant.now();
        if (!credentialSecurity.valid(token, account) || account == null || !account.isActive() || account.hasRole("SUPER_ADMIN") || !"PASSWORD_RESET".equals(token.getTokenType()) || !token.isUsableAt(now)) throw invalidReset();
        if (passwords.matches(newPassword, account.getPasswordHash())) throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同。");
        account.changePassword(passwords.encode(newPassword));
        token.consume(now);
        revocations.revoke(account);
        audit.record(account.getId(), "ACCOUNT", "PASSWORD_RESET_COMPLETED", "ACCOUNT", account.getId(), "{}", "{\"sessionsRevoked\":true}", null, requestId);
        return account.getLoginId();
    }

    private void deleteSessions(String loginId) { jdbc.update("delete from SPRING_SESSION where PRINCIPAL_NAME = ?", loginId); }
    private void requireDifferentAndValid(String current, String next) {
        if (next == null || next.length() < 12 || next.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_FAILED", "密码长度必须为 12 到 128 个字符。");
        if (next.equals(current)) throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同。");
    }
    private ApiException invalidReset() { return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_INVALID", "密码重置凭据无效、已使用或已过期。"); }
}
