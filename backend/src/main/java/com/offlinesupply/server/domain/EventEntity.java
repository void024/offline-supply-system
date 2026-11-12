package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The append-only ledger. No update or delete path exists anywhere in the codebase;
 * corrections are new events (ADJUSTMENT), never edits.
 */
@Entity
@Table(name = "events")
public class EventEntity extends AuditTimestamps {

    /** Device-generated for DEVICE events; the resolution_id for SERVER events. */
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "origin", nullable = false, columnDefinition = "text")
    private EventOrigin origin;

    /** Assigned only through the serialized sync_counters path. */
    @Column(name = "server_seq", nullable = false, unique = true)
    private long serverSeq;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "device_seq")
    private Integer deviceSeq;

    /** Unverified claim for DEVICE events; NULL for SERVER events. */
    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    /** Session user for DEVICE events; acting supervisor for SERVER events. */
    @Column(name = "pushed_by_user_id", nullable = false)
    private UUID pushedByUserId;

    @Column(name = "type", nullable = false, columnDefinition = "text")
    private EventType type;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Soft link, deliberately not a foreign key (see R4). */
    @Column(name = "planned_issue_id")
    private UUID plannedIssueId;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    /** Device clock for DEVICE events (advisory); server time for SERVER events. */
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected EventEntity() {
    }

    public EventEntity(UUID eventId, EventOrigin origin, long serverSeq, UUID deviceId, Integer deviceSeq,
                       UUID recordedByUserId, UUID pushedByUserId, EventType type, UUID itemId, int quantity,
                       UUID plannedIssueId, String reason, OffsetDateTime occurredAt, OffsetDateTime receivedAt) {
        this.eventId = eventId;
        this.origin = origin;
        this.serverSeq = serverSeq;
        this.deviceId = deviceId;
        this.deviceSeq = deviceSeq;
        this.recordedByUserId = recordedByUserId;
        this.pushedByUserId = pushedByUserId;
        this.type = type;
        this.itemId = itemId;
        this.quantity = quantity;
        this.plannedIssueId = plannedIssueId;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public EventOrigin getOrigin() {
        return origin;
    }

    public long getServerSeq() {
        return serverSeq;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public Integer getDeviceSeq() {
        return deviceSeq;
    }

    public UUID getRecordedByUserId() {
        return recordedByUserId;
    }

    public UUID getPushedByUserId() {
        return pushedByUserId;
    }

    public EventType getType() {
        return type;
    }

    public UUID getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public UUID getPlannedIssueId() {
        return plannedIssueId;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }
}
