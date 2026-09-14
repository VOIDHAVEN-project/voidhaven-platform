package cn.voidhaven.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

/** Keeps only HMAC'd, short-lived network subjects; raw addresses never enter persistence or logs. */
@Service
public class PublicWriteRateLimiter {
    private static final String HMAC = "HmacSHA256";
    private final String secret;
    private final int maximumRequests;
    private final Duration window;
    private final RateLimitLockService locks;

    public PublicWriteRateLimiter(
            @Value("${voidhaven.public-write-rate-limit-secret:}") String secret,
            @Value("${voidhaven.public-write-rate-limit-max-requests:8}") int maximumRequests,
            @Value("${voidhaven.public-write-rate-limit-window-seconds:3600}") long windowSeconds,
            Environment environment,
            RateLimitLockService locks) {
        if (maximumRequests < 1 || windowSeconds < 1) throw new IllegalStateException("Public write rate-limit configuration is invalid");
        if (isProduction(environment) && (secret == null || secret.isBlank() || "local-development-rate-limit-secret".equals(secret))) throw new IllegalStateException("A production public-write rate-limit secret is required");
        this.secret = secret == null || secret.isBlank() ? "local-development-rate-limit-secret" : secret;
        this.maximumRequests = maximumRequests;
        this.window = Duration.ofSeconds(windowSeconds);
        this.locks = locks;
    }

    public void requireAvailable(String networkSubject) {
        locks.reservePublicWrite(subjectHash(networkSubject), maximumRequests, window);
    }

    String subjectHash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash public-write subject", error);
        }
    }

    private boolean isProduction(Environment environment) {
        for (String profile : environment.getActiveProfiles()) if ("production".equals(profile)) return true;
        return false;
    }
}
