package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Server-side session state behind the HttpOnly cookie. The Prompt 2 contract fixes
 * cookie-based sessions but deliberately leaves session storage to the implementation;
 * this table is that implementation detail, documented in the companion document.
 * Only the SHA-256 hash of the bearer token is stored.
 */
@Entity
@Table(name = "sessions")
public class SessionEntity extends AuditTimestamps {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "text")
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected SessionEntity() {
    }

    public SessionEntity(UUID id, String tokenHash, UUID userId, UUID deviceId, OffsetDateTime expiresAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
