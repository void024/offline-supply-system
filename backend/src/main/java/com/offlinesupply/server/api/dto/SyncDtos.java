package com.offlinesupply.server.api.dto;

import com.offlinesupply.server.api.dto.DiscrepancyDtos.DiscrepancyDto;
import com.offlinesupply.server.api.dto.EventDtos.SupplyEventDto;
import com.offlinesupply.server.api.dto.ItemDtos.ItemDto;
import com.offlinesupply.server.api.dto.IssuerDtos.IssuerStatusDto;

import java.util.List;
import java.util.UUID;

/** Contract endpoint 4 (sync pull). */
public final class SyncDtos {

    private SyncDtos() {
    }

    /** Minimal projection for the reference snapshot (Prompt 3 ASSUMPTION, resolved here). */
    public record SubmissionIssueDto(UUID claimedEventId, String kind, String detail) {
    }

    public record ReferenceSnapshot(
            List<ItemDto> items,
            IssuerStatusDto issuer,
            List<DiscrepancyDto> discrepancies,
            List<SubmissionIssueDto> submissionIssues) {
    }

    public record PullResponse(
            List<SupplyEventDto> events,
            long nextCursor,
            boolean hasMore,
            ReferenceSnapshot reference,
            long referenceAsOf) {
    }
}
