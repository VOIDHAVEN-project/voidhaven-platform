package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublicStatusQueryRateLimiterTest {
    @Test void uses_a_separate_status_query_bucket() {
        RateLimitLockService locks = mock(RateLimitLockService.class);
        PublicStatusQueryRateLimiter limiter = new PublicStatusQueryRateLimiter(
                "a-test-only-high-entropy-secret", 60, 3600, new MockEnvironment(), locks);
        limiter.requireAvailable("203.0.113.55");
        verify(locks).reservePublicStatusQuery(org.mockito.ArgumentMatchers.anyString(), eq(60), any(Duration.class));
    }

    @Test void surfaces_status_query_limit_without_consuming_write_quota() {
        RateLimitLockService locks = mock(RateLimitLockService.class);
        doThrow(new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_STATUS_QUERY_RATE_LIMITED", "查询过于频繁，请稍后再试。"))
                .when(locks).reservePublicStatusQuery(org.mockito.ArgumentMatchers.anyString(), eq(60), any(Duration.class));
        PublicStatusQueryRateLimiter limiter = new PublicStatusQueryRateLimiter(
                "a-test-only-high-entropy-secret", 60, 3600, new MockEnvironment(), locks);
        assertThatThrownBy(() -> limiter.requireAvailable("203.0.113.55"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "PUBLIC_STATUS_QUERY_RATE_LIMITED");
    }
}
