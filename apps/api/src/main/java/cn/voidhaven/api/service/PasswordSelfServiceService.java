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

import java.net.IDN;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PasswordSelfServiceService {
    public record RecoveryEmailStatus(boolean available, boolean verified, String maskedEmail) { }
    public record VerificationIssued(Instant expiresAt) { }

    private static final String VERIFY_TYPE = "RECOVERY_EMAIL_VERIFY";
    private static final String VERIFIED_CONTEXT = "RECOVERY_EMAIL_VERIFIED:";
    private static final String PENDING_CONTEXT = "RECOVERY_EMAIL_PENDING:";

    private final AccountRepository accounts;
    private final ActivationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PrivateValueCipher privateValues;
    private final PasswordRecoveryMailService mail;
    private final PasswordService passwords;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PasswordSelfServiceService(AccountRepository accounts, ActivationTokenRepository tokens,
            PasswordEncoder passwordEncoder, PrivateValueCipher privateValues, PasswordRecoveryMailService mail,
            PasswordService passwords, JdbcTemplate jdbc, AuditService audit) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.privateValues = privateValues;
        this.mail = mail;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public boolean isOperational() {
        return mail.isOperational() && privateValues.isKeyRingOperational();
    }

    @Transactional(readOnly = true)
    public RecoveryEmailStatus status(String loginId) {
        Account account = activeAccount(loginId);
        if (!isOperational()) return new RecoveryEmailStatus(false, false, null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select verified_email_ciphertext, verified_email_key_id from account_recovery_emails where account_id = ?",
                account.getId());
        if (rows.isEmpty() || rows.get(0).get("verified_email_ciphertext") == null) {
            return new RecoveryEmailStatus(true, false, null);
        }
        Map<String, Object> row = rows.get(0);
        String email = privateValues.decrypt(String.valueOf(row.get("verified_email_ciphertext")),
                String.valueOf(row.get("verified_email_key_id")), VERIFIED_CONTEXT + account.getId());
        return new RecoveryEmailStatus(true, true, mask(email));
    }

    @Transactional
    public VerificationIssued requestVerification(String loginId, String currentPassword, String rawEmail,
                                                   String requestId) {
        requireOperational();
        Account account = activeAccountForUpdate(loginId);
        requirePassword(account, currentPassword);
        String email = normalizeEmail(rawEmail);
        PrivateValueCipher.EncryptedValue encrypted = privateValues.encrypt(email, PENDING_CONTEXT + account.getId());
        Instant now = Instant.now();
        jdbc.update("insert into account_recovery_emails (account_id, pending_email_ciphertext, pending_email_key_id, pending_requested_at) "
                        + "values (?, ?, ?, ?) on duplicate key update pending_email_ciphertext = values(pending_email_ciphertext), "
                        + "pending_email_key_id = values(pending_email_key_id), pending_requested_at = values(pending_requested_at)",
                account.getId(), encrypted.ciphertext(), encrypted.keyId(), now);
        tokens.invalidateUnusedByType(account.getId(), VERIFY_TYPE, now);
        String rawToken = SecureTokens.randomUrlToken();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
        tokens.save(new ActivationToken(Ids.ulid(), account.getId(), VERIFY_TYPE, SecureTokens.sha256(rawToken), expiresAt));
        mail.sendVerificationCode(email, rawToken, expiresAt);
        audit.record(account.getId(), role(account), "RECOVERY_EMAIL_VERIFICATION_ISSUED", "ACCOUNT", account.getId(),
                "{}", "{\"expiresInMinutes\":15}", null, requestId);
        return new VerificationIssued(expiresAt);
    }

    @Transactional
    public RecoveryEmailStatus verify(String loginId, String rawToken, String requestId) {
        requireOperational();
        Account account = activeAccountForUpdate(loginId);
        ActivationToken token = tokens.findByTokenHashForUpdate(SecureTokens.sha256(rawToken))
                .filter(value -> VERIFY_TYPE.equals(value.getTokenType()))
                .filter(value -> account.getId().equals(value.getAccountId()))
                .filter(value -> value.isUsableAt(Instant.now()))
                .orElseThrow(this::invalidVerification);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select pending_email_ciphertext, pending_email_key_id from account_recovery_emails where account_id = ? for update",
                account.getId());
        if (rows.isEmpty() || rows.get(0).get("pending_email_ciphertext") == null) throw invalidVerification();
        Map<String, Object> row = rows.get(0);
        String email = privateValues.decrypt(String.valueOf(row.get("pending_email_ciphertext")),
                String.valueOf(row.get("pending_email_key_id")), PENDING_CONTEXT + account.getId());
        PrivateValueCipher.EncryptedValue verified = privateValues.encrypt(email, VERIFIED_CONTEXT + account.getId());
        Instant now = Instant.now();
        jdbc.update("update account_recovery_emails set verified_email_ciphertext = ?, verified_email_key_id = ?, "
                        + "verified_at = ?, pending_email_ciphertext = null, pending_email_key_id = null, pending_requested_at = null "
                        + "where account_id = ?",
                verified.ciphertext(), verified.keyId(), now, account.getId());
        token.consume(now);
        tokens.invalidateUnusedByTypeExcept(account.getId(), VERIFY_TYPE, token.getId(), now);
        audit.record(account.getId(), role(account), "RECOVERY_EMAIL_VERIFIED", "ACCOUNT", account.getId(),
                "{}", "{\"verified\":true}", null, requestId);
        return new RecoveryEmailStatus(true, true, mask(email));
    }

    @Transactional
    public void remove(String loginId, String currentPassword, String requestId) {
        Account account = activeAccountForUpdate(loginId);
        requirePassword(account, currentPassword);
        jdbc.update("delete from account_recovery_emails where account_id = ?", account.getId());
        tokens.invalidateUnusedByType(account.getId(), VERIFY_TYPE, Instant.now());
        audit.record(account.getId(), role(account), "RECOVERY_EMAIL_REMOVED", "ACCOUNT", account.getId(),
                "{\"verified\":true}", "{\"verified\":false}", null, requestId);
    }

    @Transactional
    public void requestPasswordReset(String loginId, String requestId) {
        if (!isOperational()) return;
        Account account = accounts.findByLoginId(loginId.trim().toLowerCase(Locale.ROOT))
                .filter(Account::isActive).orElse(null);
        if (account == null) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select verified_email_ciphertext, verified_email_key_id from account_recovery_emails "
                        + "where account_id = ? and verified_at is not null",
                account.getId());
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.get(0);
        String email = privateValues.decrypt(String.valueOf(row.get("verified_email_ciphertext")),
                String.valueOf(row.get("verified_email_key_id")), VERIFIED_CONTEXT + account.getId());
        PasswordService.IssuedReset issued = passwords.issueSelfServiceReset(account.getId(), requestId);
        mail.sendPasswordResetToken(email, issued.resetToken(), issued.expiresAt());
    }

    private Account activeAccount(String loginId) {
        return accounts.findByLoginId(loginId).filter(Account::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号不可用。"));
    }

    private Account activeAccountForUpdate(String loginId) {
        Account account = activeAccount(loginId);
        return accounts.findByIdForUpdate(account.getId()).filter(Account::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号不可用。"));
    }

    private void requirePassword(Account account, String password) {
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "当前密码不正确。");
        }
    }

    private void requireOperational() {
        if (!isOperational()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PASSWORD_SELF_SERVICE_UNAVAILABLE",
                    "自助密码恢复尚未安全配置，请继续使用人工交付服务。");
        }
    }

    private String normalizeEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int at = email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1 || email.length() > 254) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECOVERY_EMAIL_INVALID", "恢复邮箱格式无效。");
        }
        try {
            String local = email.substring(0, at);
            String domain = IDN.toASCII(email.substring(at + 1));
            if (local.length() > 64 || !domain.contains(".") || domain.length() > 253
                    || !local.matches("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+$")
                    || !domain.matches("^[a-z0-9.-]+$") || domain.startsWith(".") || domain.endsWith(".")) {
                throw new IllegalArgumentException();
            }
            return local + "@" + domain;
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECOVERY_EMAIL_INVALID", "恢复邮箱格式无效。");
        }
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    private String role(Account account) {
        return account.hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : account.hasRole("ADMIN") ? "ADMIN" : "PARTNER";
    }

    private ApiException invalidVerification() {
        return new ApiException(HttpStatus.BAD_REQUEST, "RECOVERY_EMAIL_VERIFICATION_INVALID",
                "恢复邮箱验证码无效、已使用或已过期。");
    }
}
