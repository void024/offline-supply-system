package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "devices")
public class DeviceEntity extends AuditTimestamps {

    /** Client-generated device_id; the primary key by design (contract). */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "label", columnDefinition = "text")
    private String label;

    /** Sensitive: SHA-256 of the one-time device_key; the raw key is never stored. */
    @Column(name = "device_key_hash", nullable = false, columnDefinition = "text")
    private String deviceKeyHash;

    @Column(name = "enrolled_by_user_id", nullable = false)
    private UUID enrolledByUserId;

    protected DeviceEntity() {
    }

    public DeviceEntity(UUID id, String label, String deviceKeyHash, UUID enrolledByUserId) {
        this.id = id;
        this.label = label;
        this.deviceKeyHash = deviceKeyHash;
        this.enrolledByUserId = enrolledByUserId;
    }

    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDeviceKeyHash() {
        return deviceKeyHash;
    }

    public UUID getEnrolledByUserId() {
        return enrolledByUserId;
    }
}
