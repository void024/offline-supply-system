package com.offlinesupply.server.api.dto;

import com.offlinesupply.server.domain.EventOrigin;
import com.offlinesupply.server.domain.EventType;
import com.offlinesupply.server.domain.PushStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contract endpoints 3 (push) and 6 (history browse). */
public final class EventDtos {

    private EventDtos() {
    }

    public record EventInputDto(
            @NotNull UUID eventId,
            @NotNull Integer deviceSeq,
            @NotNull UUID recordedByUserId,
            @NotNull EventType type,
            @NotNull UUID itemId,
            @NotNull Integer quantity,
            UUID plannedIssueId,
            String reason,
            @NotNull OffsetDateTime occurredAt) {
    }

    public record PushEventsRequest(
            @NotNull @Size(min = 1, max = 500) List<@Valid EventInputDto> events) {
    }

    public record EventResult(UUID eventId, PushStatus status, String detail) {
    }

    public record PushEventsResponse(List<EventResult> results) {
    }

    /** Server-delivered event: device input plus server-assigned provenance. */
    public record SupplyEventDto(
            UUID eventId,
            Integer deviceSeq,
            UUID recordedByUserId,
            EventType type,
            UUID itemId,
            int quantity,
            UUID plannedIssueId,
            String reason,
            OffsetDateTime occurredAt,
            long serverSeq,
            EventOrigin origin,
            OffsetDateTime receivedAt) {
    }

    /**
     * History envelope: the contract fixes the query parameters of endpoint 6 but not
     * its response envelope; this mirrors the pull's event array (was an ASSUMPTION in
     * Prompt 3; resolved here in the backend's favour).
     */
    public record EventHistoryResponse(List<SupplyEventDto> events) {
    }
}
