package com.offlinesupply.server.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Contract endpoints 9-10. */
public final class IssuerDtos {

    private IssuerDtos() {
    }

    public record DesignateRequest(@NotNull UUID deviceId) {
    }

    public record DesignateResponse(UUID deviceId, OffsetDateTime designatedAt) {
    }

    public record IssuerStatusDto(UUID deviceId, OffsetDateTime designatedAt) {
    }
}
