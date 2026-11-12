package com.offlinesupply.server.service;

import com.offlinesupply.server.config.AppProperties;
import com.offlinesupply.server.exception.ApiException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login guard (contract: the one rate-limited endpoint): 5 failed attempts per minute
 * per client key -> 429 with Retry-After. In-memory sliding window, which is honest for
 * v1's single-server deployment; a multi-instance deployment would need a shared store.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowSeconds;
    private final Map<String, Deque<Instant>> failuresByKey = new ConcurrentHashMap<>();

    public LoginRateLimiter(AppProperties properties) {
        this.maxAttempts = properties.login().maxAttempts();
        this.windowSeconds = properties.login().windowSeconds();
    }

    public synchronized void checkAllowed(String clientKey) {
        Deque<Instant> failures = failuresByKey.get(clientKey);
        if (failures == null) {
            return;
        }
        evictExpired(failures);
        if (failures.size() >= maxAttempts) {
            Instant oldest = failures.peekFirst();
            long retryAfter = Math.max(1, windowSeconds - (Instant.now().getEpochSecond() - oldest.getEpochSecond()));
            throw ApiException.tooManyRequests(
                    "too many failed login attempts; retry after " + retryAfter + " seconds", (int) retryAfter);
        }
    }

    public synchronized void recordFailure(String clientKey) {
        Deque<Instant> failures = failuresByKey.computeIfAbsent(clientKey, k -> new ArrayDeque<>());
        evictExpired(failures);
        failures.addLast(Instant.now());
    }

    public synchronized void clear(String clientKey) {
        failuresByKey.remove(clientKey);
    }

    private void evictExpired(Deque<Instant> failures) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.pollFirst();
        }
    }
}
