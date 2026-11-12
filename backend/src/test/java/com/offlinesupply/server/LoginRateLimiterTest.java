package com.offlinesupply.server;

import com.offlinesupply.server.config.AppProperties;
import com.offlinesupply.server.exception.ApiException;
import com.offlinesupply.server.service.LoginRateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    private LoginRateLimiter newLimiter() {
        AppProperties props = new AppProperties(
                new AppProperties.Session("SUPPLY_SESSION", 24, false),
                new AppProperties.Login(5, 60),
                new AppProperties.Sync(500, 500));
        return new LoginRateLimiter(props);
    }

    @Test
    void allowsUpToFiveFailuresThenBlocks() {
        LoginRateLimiter limiter = newLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("ip");
        }
        ApiException ex = assertThrows(ApiException.class, () -> limiter.checkAllowed("ip"));
        assertEquals(429, ex.getStatus().value());
        assertTrue(ex.getRetryAfterSeconds() != null && ex.getRetryAfterSeconds() >= 1);
    }

    @Test
    void fourthFailureStillAllowed() {
        LoginRateLimiter limiter = newLimiter();
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("ip");
        }
        assertDoesNotThrow(() -> limiter.checkAllowed("ip"));
    }

    @Test
    void successfulLoginClearsTheWindow() {
        LoginRateLimiter limiter = newLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("ip");
        }
        limiter.clear("ip");
        assertDoesNotThrow(() -> limiter.checkAllowed("ip"));
    }

    @Test
    void differentClientsAreIndependent() {
        LoginRateLimiter limiter = newLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("ip-a");
        }
        assertThrows(ApiException.class, () -> limiter.checkAllowed("ip-a"));
        assertDoesNotThrow(() -> limiter.checkAllowed("ip-b"));
    }
}
