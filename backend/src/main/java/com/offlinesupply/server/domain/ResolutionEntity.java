package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The audited review record. resolution_id doubles as the ADJUSTMENT event's event_id. */
@Entity
@Table(name = "resolutions")
public class ResolutionEntity extends AuditTimestamps {

    @Id
    @Column(name = "resolution_id", nullable = false)
    private UUID resolutionId;

    @Column(name = "discrepancy_id", nullable = false)
    private UUID discrepancyId;

    @Column(name = "outcome", nullable = false, columnDefinition = "text")
    private ResolutionOutcome outcome;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "quantity_delta")
    private Integer quantityDelta;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "adjustment_event_id")
    private UUID adjustmentEventId;

    protected ResolutionEntity() {
    }

    public ResolutionEntity(UUID resolutionId, UUID discrepancyId, ResolutionOutcome outcome, String reason,
                            Integer quantityDelta, UUID createdByUserId, UUID deviceId, UUID adjustmentEventId) {
        this.resolutionId = resolutionId;
        this.discrepancyId = discrepancyId;
        this.outcome = outcome;
        this.reason = reason;
        this.quantityDelta = quantityDelta;
        this.createdByUserId = createdByUserId;
        this.deviceId = deviceId;
        this.adjustmentEventId = adjustmentEventId;
    }

    public UUID getResolutionId() {
        return resolutionId;
    }

    public UUID getDiscrepancyId() {
        return discrepancyId;
    }

    public ResolutionOutcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public Integer getQuantityDelta() {
        return quantityDelta;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getAdjustmentEventId() {
        return adjustmentEventId;
    }
}
