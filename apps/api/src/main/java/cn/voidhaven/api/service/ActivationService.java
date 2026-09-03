package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.ActivationToken;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ActivationTokenRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
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
    private static final Duration VALIDITY = Duration.ofHours(24);
    private final ActivationTokenRepository tokens;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();

    public ActivationService(ActivationTokenRepository tokens, AccountRepository accounts, PasswordEncoder passwords) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional
    public String issue(String accountId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new ActivationToken(Ids.ulid(), accountId, hash(raw), Instant.now().plus(VALIDITY)));
        return raw;
    }

    @Transactional
    public Account consume(String rawToken, String newPassword) {
        ActivationToken token = tokens.findByTokenHashForUpdate(hash(rawToken)).orElseThrow(this::invalidToken);
        Instant now = Instant.now();
        if (!token.isUsableAt(now)) throw invalidToken();
        Account account = accounts.findById(token.getAccountId()).orElseThrow(this::invalidToken);
        token.consume(now);
        account.activateWithPassword(passwords.encode(newPassword));
        return account;
    }

    private String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("Unable to hash activation token", error); }
    }

    private ApiException invalidToken() { return new ApiException(HttpStatus.BAD_REQUEST, "ACTIVATION_TOKEN_INVALID", "激活链接无效、已使用或已过期，请联系管理员重新生成。"); }
}
