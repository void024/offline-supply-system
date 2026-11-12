package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discrepancies")
public class DiscrepancyEntity extends AuditTimestamps {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "type", nullable = false, columnDefinition = "text")
    private DiscrepancyType type;

    /** Stable identity of the specific facts this row is about (contract). */
    @Column(name = "evidence_key", nullable = false, columnDefinition = "text")
    private String evidenceKey;

    /** Highest server_seq of the facts this row covers (the no-reopen rule). */
    @Column(name = "evidence_seq", nullable = false)
    private long evidenceSeq;

    /** Human-readable explanation; stored as a jsonb string scalar. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonStringScalarConverter.class)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private String details;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private DiscrepancyStatus status;

    @Column(name = "resolved_by_resolution_id")
    private UUID resolvedByResolutionId;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    protected DiscrepancyEntity() {
    }

    public DiscrepancyEntity(UUID id, UUID itemId, DiscrepancyType type, String evidenceKey, long evidenceSeq,
                             String details, DiscrepancyStatus status, UUID resolvedByResolutionId,
                             OffsetDateTime detectedAt) {
        this.id = id;
        this.itemId = itemId;
        this.type = type;
        this.evidenceKey = evidenceKey;
        this.evidenceSeq = evidenceSeq;
        this.details = details;
        this.status = status;
        this.resolvedByResolutionId = resolvedByResolutionId;
        this.detectedAt = detectedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public DiscrepancyType getType() {
        return type;
    }

    public String getEvidenceKey() {
        return evidenceKey;
    }

    public long getEvidenceSeq() {
        return evidenceSeq;
    }

    public String getDetails() {
        return details;
    }

    public DiscrepancyStatus getStatus() {
        return status;
    }

    public UUID getResolvedByResolutionId() {
        return resolvedByResolutionId;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public void updateEvidence(long newEvidenceSeq, String newDetails) {
        this.evidenceSeq = newEvidenceSeq;
        this.details = newDetails;
    }

    public void markResolved(UUID resolutionId) {
        this.status = DiscrepancyStatus.RESOLVED;
        this.resolvedByResolutionId = resolutionId;
    }
}
