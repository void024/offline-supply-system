package com.offlinesupply.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "items")
public class ItemEntity extends AuditTimestamps {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "unit", nullable = false, columnDefinition = "text")
    private String unit;

    @Column(name = "opening_qty", nullable = false)
    private int openingQty;

    protected ItemEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public int getOpeningQty() {
        return openingQty;
    }
}
