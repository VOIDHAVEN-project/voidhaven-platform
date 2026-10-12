package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.ActivationToken;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ActivationTokenRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class ActivationService {
    public record IssuedActivation(String token, Instant expiresAt) { }

    private static final Duration VALIDITY = Duration.ofHours(24);
    private final ActivationTokenRepository tokens;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private SecurityRevocationService revocations;
    private CredentialSecurityService credentialSecurity;
    private AuditService audit;
    @org.springframework.beans.factory.annotation.Autowired
    void audit(AuditService audit) { this.audit = audit; }
    private final SecureRandom random = new SecureRandom();

    public ActivationService(ActivationTokenRepository tokens, AccountRepository accounts, PasswordEncoder passwords,
                             JdbcTemplate jdbc) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.revocations = new SecurityRevocationService(tokens, jdbc);
        this.credentialSecurity = new CredentialSecurityService(accounts);
    }

    @org.springframework.beans.factory.annotation.Autowired
    void security(SecurityRevocationService revocations, CredentialSecurityService credentials) { this.revocations = revocations; this.credentialSecurity = credentials; }

    @Transactional
    public IssuedActivation issue(String accountId) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在。"));
        return issueLocked(account);
    }

    @Transactional
    public IssuedActivation issueLocked(Account account) {
        Instant now = Instant.now();
        tokens.invalidateUnusedByType(account.getId(), "ACTIVATION", now);
        Instant expiresAt = now.plus(VALIDITY);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ActivationToken token = new ActivationToken(Ids.ulid(), account.getId(), hash(raw), expiresAt);
        credentialSecurity.bind(token, account);
        tokens.save(token);
        return new IssuedActivation(raw, expiresAt);
    }

    @Transactional
    public Account consume(String rawToken, String newPassword) { return consume(rawToken, newPassword, Ids.ulid()); }

    @Transactional
    public Account consume(String rawToken, String newPassword, String requestId) {
        String tokenHash = hash(rawToken);
        ActivationToken preview = tokens.findByTokenHash(tokenHash).orElseThrow(this::invalidToken);
        Account account = credentialSecurity.lockTarget(preview);
        ActivationToken token = tokens.findByTokenHashForUpdate(tokenHash).orElseThrow(this::invalidToken);
        Instant now = Instant.now();
        if (!credentialSecurity.valid(token, account) || !token.isUsableAt(now)) throw invalidToken();
        if (!"ACTIVATION".equals(token.getTokenType())) throw invalidToken();
        if (!account.isPending()) throw invalidToken();
        account.activateWithPassword(passwords.encode(newPassword));
        token.consume(now);
        revocations.revoke(account);
        completePartnerApplication(account.getId());
        if (audit != null) audit.record(account.getId(), account.hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : account.hasRole("ADMIN") ? "ADMIN" : "PARTNER",
                "ACTIVATION_COMPLETED", "ACCOUNT", account.getId(), "{\"qualificationStatus\":\"PENDING\"}", "{\"qualificationStatus\":\"ACTIVE\"}", "账号使用者完成激活", requestId);
        return account;
    }

    private void completePartnerApplication(String accountId) {
        for (var application : jdbc.queryForList(
                "select id, state from partner_applications where provisioned_account_id = ? and state = 'ACTIVATION_PENDING' for update",
                accountId)) {
            String applicationId = String.valueOf(application.get("id"));
            String from = String.valueOf(application.get("state"));
            int updated = jdbc.update("update partner_applications set state = 'ACTIVATED', activated_at = current_timestamp(6), resolved_at = current_timestamp(6), public_result = '账号已完成激活。', version = version + 1 where id = ? and provisioned_account_id = ? and state = 'ACTIVATION_PENDING'",
                    applicationId, accountId);
            if (updated == 1) {
                jdbc.update("insert into public_case_status_history (id, case_type, case_id, from_state, to_state, public_message) values (?, 'PARTNER_APPLICATION', ?, ?, 'ACTIVATED', '账号已完成激活。')",
                        Ids.ulid(), applicationId, from);
            }
        }
    }

    private String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("Unable to hash activation token", error); }
    }

    private ApiException invalidToken() { return new ApiException(HttpStatus.BAD_REQUEST, "ACTIVATION_TOKEN_INVALID", "激活链接无效、已使用或已过期，请联系管理员重新生成。"); }
}
