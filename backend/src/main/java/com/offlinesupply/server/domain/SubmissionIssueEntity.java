package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The quarantine/report table: nothing is silently dropped, retries never multiply rows
 * (UNIQUE (claimed_event_id, kind)).
 */
@Entity
@Table(name = "submission_issues")
public class SubmissionIssueEntity extends AuditTimestamps {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "claimed_event_id")
    private UUID claimedEventId;

    @Column(name = "kind", nullable = false, columnDefinition = "text")
    private IssueKind kind;

    /** The verbatim rejected submission. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    protected SubmissionIssueEntity() {
    }

    public SubmissionIssueEntity(UUID id, UUID deviceId, UUID claimedEventId, IssueKind kind,
                                 String payload, String detail, OffsetDateTime detectedAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.claimedEventId = claimedEventId;
        this.kind = kind;
        this.payload = payload;
        this.detail = detail;
        this.detectedAt = detectedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getClaimedEventId() {
        return claimedEventId;
    }

    public IssueKind getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public String getDetail() {
        return detail;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }
}
