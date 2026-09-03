package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicWriteRateLimiterTest {
    @Test void limits_a_hashed_network_subject_without_storing_the_raw_value() {
        PublicWriteRateLimiter limiter = new PublicWriteRateLimiter("a-test-only-high-entropy-secret", 2, 3600, new MockEnvironment());
        limiter.requireAvailable("203.0.113.55");
        limiter.requireAvailable("203.0.113.55");
        assertThatThrownBy(() -> limiter.requireAvailable("203.0.113.55"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("过于频繁");
    }

    @Test void refuses_the_development_secret_when_the_production_profile_is_active() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("production");
        assertThatThrownBy(() -> new PublicWriteRateLimiter("local-development-rate-limit-secret", 2, 3600, production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production");
    }
}
