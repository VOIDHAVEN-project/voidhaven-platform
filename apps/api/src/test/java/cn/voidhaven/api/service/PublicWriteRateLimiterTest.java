package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PublicWriteRateLimiterTest {
    @Test void limits_a_hashed_network_subject_without_storing_the_raw_value() {
        RateLimitLockService locks = mock(RateLimitLockService.class);
        doNothing().doNothing().doThrow(new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_WRITE_RATE_LIMITED", "提交过于频繁，请稍后再试。"))
                .when(locks).reservePublicWrite(anyString(), eq(2), any(Duration.class));
        PublicWriteRateLimiter limiter = new PublicWriteRateLimiter("a-test-only-high-entropy-secret", 2, 3600, new MockEnvironment(), locks);
        limiter.requireAvailable("203.0.113.55");
        limiter.requireAvailable("203.0.113.55");
        assertThatThrownBy(() -> limiter.requireAvailable("203.0.113.55"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("过于频繁");
    }

    @Test void refuses_the_development_secret_when_the_production_profile_is_active() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("production");
        assertThatThrownBy(() -> new PublicWriteRateLimiter("local-development-rate-limit-secret", 2, 3600, production, mock(RateLimitLockService.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production");
    }
}
