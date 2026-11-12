package com.offlinesupply.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed application settings (constructor-bound). Values live in application.yml and
 * are overridable through environment variables; no secret defaults anywhere.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Session session, Login login, Sync sync) {

    public record Session(String cookieName, long ttlHours, boolean secureCookie) {
    }

    public record Login(int maxAttempts, long windowSeconds) {
    }

    public record Sync(int maxBatchSize, int maxPullLimit) {
    }
}
