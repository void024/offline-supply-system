package com.offlinesupply.server.api.dto;

import com.offlinesupply.server.domain.DiscrepancyStatus;
import com.offlinesupply.server.domain.DiscrepancyType;
import com.offlinesupply.server.domain.ResolutionOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contract endpoints 7-8. */
public final class DiscrepancyDtos {

    private DiscrepancyDtos() {
    }

    /** Embedded review record (Prompt 3 ASSUMPTION, resolved here): body fields plus resolved_at. */
    public record ResolutionRecordDto(
            UUID resolutionId,
            ResolutionOutcome outcome,
            String reason,
            Integer quantityDelta,
            OffsetDateTime resolvedAt) {
    }

    public record DiscrepancyDto(
            UUID id,
            UUID itemId,
            DiscrepancyType type,
            String details,
            DiscrepancyStatus status,
            OffsetDateTime detectedAt,
            ResolutionRecordDto resolution) {
    }

    public record DiscrepanciesResponse(List<DiscrepancyDto> discrepancies) {
    }

    public record ResolveRequest(
            @NotNull UUID resolutionId,
            @NotNull ResolutionOutcome outcome,
            @NotBlank String reason,
            Integer quantityDelta) {
    }

    public record ResolveResponse(UUID resolutionId, String status, UUID adjustmentEventId) {
    }
}
