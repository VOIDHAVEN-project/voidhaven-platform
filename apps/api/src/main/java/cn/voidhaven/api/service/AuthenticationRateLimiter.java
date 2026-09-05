package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class AuthenticationRateLimiter {
    private static final String HMAC = "HmacSHA256";
    private final JdbcTemplate jdbc;
    private final String secret;
    private final int loginFailureLimit;
    private final int loginNetworkLimit;
    private final int activationNetworkLimit;
    private final long windowMinutes;

    public AuthenticationRateLimiter(JdbcTemplate jdbc,
            @Value("${voidhaven.public-write-rate-limit-secret:local-development-rate-limit-secret}") String secret,
            @Value("${voidhaven.auth-login-failure-limit:5}") int loginFailureLimit,
            @Value("${voidhaven.auth-login-network-limit:20}") int loginNetworkLimit,
            @Value("${voidhaven.auth-activation-network-limit:10}") int activationNetworkLimit,
            @Value("${voidhaven.auth-rate-limit-window-minutes:15}") long windowMinutes) {
        if (loginFailureLimit < 1 || loginNetworkLimit < 1 || activationNetworkLimit < 1 || windowMinutes < 1) {
            throw new IllegalStateException("Authentication rate-limit configuration is invalid");
        }
        this.jdbc = jdbc;
        this.secret = secret;
        this.loginFailureLimit = loginFailureLimit;
        this.loginNetworkLimit = loginNetworkLimit;
        this.activationNetworkLimit = activationNetworkLimit;
        this.windowMinutes = windowMinutes;
    }

    public void beginLogin(String loginId, String networkSubject) {
        cleanup();
        String networkHash = hash("network:" + networkSubject);
        String loginHash = hash("login:" + loginId.trim().toLowerCase());
        requireBelow("LOGIN_NETWORK", networkHash, loginNetworkLimit);
        requireBelow("LOGIN_ID", loginHash, loginFailureLimit);
        record("LOGIN_NETWORK", networkHash, "ATTEMPT");
    }

    public void loginFailed(String loginId) {
        record("LOGIN_ID", hash("login:" + loginId.trim().toLowerCase()), "FAILURE");
    }

    public void loginSucceeded(String loginId) {
        String subject = hash("login:" + loginId.trim().toLowerCase());
        jdbc.update("delete from auth_rate_limit_events where scope = 'LOGIN_ID' and subject_hash = ?", subject);
        record("LOGIN_ID", subject, "SUCCESS");
    }

    public void beginActivation(String networkSubject) {
        cleanup();
        String networkHash = hash("activation:" + networkSubject);
        requireBelow("ACTIVATION_NETWORK", networkHash, activationNetworkLimit);
        record("ACTIVATION_NETWORK", networkHash, "ATTEMPT");
    }

    private void requireBelow(String scope, String subjectHash, int maximum) {
        Integer count = jdbc.queryForObject("select count(*) from auth_rate_limit_events where scope = ? and subject_hash = ? and outcome in ('ATTEMPT','FAILURE') and expires_at > current_timestamp(6)", Integer.class, scope, subjectHash);
        if (count != null && count >= maximum) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "尝试过于频繁，请在 15 分钟后重试。");
    }

    private void record(String scope, String subjectHash, String outcome) {
        jdbc.update("insert into auth_rate_limit_events (id, scope, subject_hash, outcome, expires_at) values (?, ?, ?, ?, ?)",
                Ids.ulid(), scope, subjectHash, outcome, Timestamp.from(Instant.now().plus(windowMinutes, ChronoUnit.MINUTES)));
    }

    private void cleanup() { jdbc.update("delete from auth_rate_limit_events where expires_at < current_timestamp(6)"); }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("Cannot hash authentication subject", error); }
    }
}
