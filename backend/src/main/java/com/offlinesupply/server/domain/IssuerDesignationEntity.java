package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Singleton table: one row, ever. Concurrent second inserts fail on the PK. */
@Entity
@Table(name = "issuer_designations")
public class IssuerDesignationEntity extends AuditTimestamps {

    public static final short SINGLETON_KEY = 1;

    @Id
    @Column(name = "singleton_key", nullable = false)
    private short singletonKey;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "designated_by_user_id", nullable = false)
    private UUID designatedByUserId;

    protected IssuerDesignationEntity() {
    }

    public IssuerDesignationEntity(UUID deviceId, UUID designatedByUserId) {
        this.singletonKey = SINGLETON_KEY;
        this.deviceId = deviceId;
        this.designatedByUserId = designatedByUserId;
    }

    public short getSingletonKey() {
        return singletonKey;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getDesignatedByUserId() {
        return designatedByUserId;
    }
}
