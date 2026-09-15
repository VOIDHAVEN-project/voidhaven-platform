package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthenticationRateLimiterTest {
    @Test void delegates_login_completion_to_the_same_subject_lock_transaction() {
        RateLimitLockService locks = mock(RateLimitLockService.class);
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(locks, "test-secret", 5, 20, 10, 15);

        limiter.loginFailed("failed-attempt");
        limiter.loginSucceeded("successful-attempt");

        verify(locks).completeLoginAttempt("failed-attempt", false);
        verify(locks).completeLoginAttempt("successful-attempt", true);
    }
}
