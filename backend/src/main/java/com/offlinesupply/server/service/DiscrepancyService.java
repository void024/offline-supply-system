package com.offlinesupply.server.service;

import com.offlinesupply.server.api.dto.DiscrepancyDtos.DiscrepanciesResponse;
import com.offlinesupply.server.api.dto.DiscrepancyDtos.DiscrepancyDto;
import com.offlinesupply.server.api.dto.DiscrepancyDtos.ResolutionRecordDto;
import com.offlinesupply.server.api.dto.DiscrepancyDtos.ResolveRequest;
import com.offlinesupply.server.api.dto.DiscrepancyDtos.ResolveResponse;
import com.offlinesupply.server.domain.DiscrepancyEntity;
import com.offlinesupply.server.domain.DiscrepancyStatus;
import com.offlinesupply.server.domain.EventEntity;
import com.offlinesupply.server.domain.EventOrigin;
import com.offlinesupply.server.domain.EventType;
import com.offlinesupply.server.domain.ResolutionEntity;
import com.offlinesupply.server.domain.ResolutionOutcome;
import com.offlinesupply.server.exception.ApiException;
import com.offlinesupply.server.repository.DiscrepancyRepository;
import com.offlinesupply.server.repository.EventRepository;
import com.offlinesupply.server.repository.ResolutionRepository;
import com.offlinesupply.server.security.SessionPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Contract endpoints 7-8. Resolve records a reasoned outcome atomically: one transaction
 * writes the resolution row - and, only for ADJUSTMENT, a SERVER-origin adjustment event
 * whose event_id IS the resolution_id - and marks the discrepancy RESOLVED.
 * Idempotent on resolution_id: same id + identical payload returns the original outcome;
 * same id + different payload (or different discrepancy) -> 409.
 */
@Service
public class DiscrepancyService {

    private static final Logger log = LoggerFactory.getLogger(DiscrepancyService.class);

    private final DiscrepancyRepository discrepancyRepository;
    private final ResolutionRepository resolutionRepository;
    private final EventRepository eventRepository;
    private final SyncCounterService syncCounter;
    private final ReconciliationService reconciliationService;

    public DiscrepancyService(DiscrepancyRepository discrepancyRepository,
                              ResolutionRepository resolutionRepository, EventRepository eventRepository,
                              SyncCounterService syncCounter, ReconciliationService reconciliationService) {
        this.discrepancyRepository = discrepancyRepository;
        this.resolutionRepository = resolutionRepository;
        this.eventRepository = eventRepository;
        this.syncCounter = syncCounter;
        this.reconciliationService = reconciliationService;
    }

    @Transactional(readOnly = true)
    public DiscrepanciesResponse list(DiscrepancyStatus statusFilter) {
        List<DiscrepancyEntity> rows = statusFilter == null
                ? discrepancyRepository.findAllByOrderByDetectedAtAsc()
                : discrepancyRepository.findByStatusOrderByDetectedAtAsc(statusFilter);
        return new DiscrepanciesResponse(toDtos(rows));
    }

    /** Used by the sync pull's reference snapshot (inside the pull's snapshot transaction). */
    public List<DiscrepancyDto> toDtos(List<DiscrepancyEntity> rows) {
        List<DiscrepancyDto> out = new ArrayList<>(rows.size());
        for (DiscrepancyEntity d : rows) {
            ResolutionRecordDto resolution = null;
            if (d.getResolvedByResolutionId() != null) {
                resolution = resolutionRepository.findById(d.getResolvedByResolutionId())
                        .map(r -> new ResolutionRecordDto(r.getResolutionId(), r.getOutcome(), r.getReason(),
                                r.getQuantityDelta(), r.getCreatedAt()))
                        .orElse(null);
            }
            out.add(new DiscrepancyDto(d.getId(), d.getItemId(), d.getType(), d.getDetails(), d.getStatus(),
                    d.getDetectedAt(), resolution));
        }
        return out;
    }

    @Transactional
    public ResolveResponse resolve(SessionPrincipal principal, UUID discrepancyId, ResolveRequest request) {
        if (request.outcome() == ResolutionOutcome.ADJUSTMENT) {
            if (request.quantityDelta() == null || request.quantityDelta() == 0) {
                throw ApiException.badRequest("quantity_delta is required and must be non-zero for ADJUSTMENT");
            }
        } else if (request.quantityDelta() != null) {
            throw ApiException.badRequest("quantity_delta is only allowed for ADJUSTMENT");
        }

        // Idempotency key first: a retry must find its original outcome even if the
        // discrepancy id in the path was mistyped on retry.
        Optional<ResolutionEntity> prior = resolutionRepository.findById(request.resolutionId());
        if (prior.isPresent()) {
            ResolutionEntity existing = prior.get();
            boolean identical = existing.getDiscrepancyId().equals(discrepancyId)
                    && existing.getOutcome() == request.outcome()
                    && Objects.equals(existing.getQuantityDelta(), request.quantityDelta())
                    && existing.getReason().equals(request.reason());
            if (!identical) {
                throw ApiException.conflict("resolution_id was already used with a different payload");
            }
            return new ResolveResponse(existing.getResolutionId(), "RESOLVED", existing.getAdjustmentEventId());
        }

        // Lock the row FIRST: a concurrent resolve with the same resolution_id waits here,
        // then finds the winner's resolution below and replays it (the contract's idempotency).
        DiscrepancyEntity discrepancy = discrepancyRepository.findByIdForUpdate(discrepancyId)
                .orElseThrow(() -> ApiException.notFound("discrepancy not found"));

        Optional<ResolutionEntity> afterLock = resolutionRepository.findById(request.resolutionId());
        if (afterLock.isPresent()) {
            ResolutionEntity existing = afterLock.get();
            boolean identical = existing.getDiscrepancyId().equals(discrepancyId)
                    && existing.getOutcome() == request.outcome()
                    && Objects.equals(existing.getQuantityDelta(), request.quantityDelta())
                    && existing.getReason().equals(request.reason());
            if (!identical) {
                throw ApiException.conflict("resolution_id was already used with a different payload");
            }
            return new ResolveResponse(existing.getResolutionId(), "RESOLVED", existing.getAdjustmentEventId());
        }
        if (discrepancy.getStatus() == DiscrepancyStatus.RESOLVED) {
            throw ApiException.conflict("discrepancy is already resolved by another resolution");
        }

        UUID adjustmentEventId = null;
        EventEntity adjustment = null;
        if (request.outcome() == ResolutionOutcome.ADJUSTMENT) {
            long nextSeq = syncCounter.lockAndRead();
            adjustment = new EventEntity(request.resolutionId(), EventOrigin.SERVER, nextSeq, null, null, null,
                    principal.userId(), EventType.ADJUSTMENT, discrepancy.getItemId(), request.quantityDelta(),
                    null, request.reason(), OffsetDateTime.now(), OffsetDateTime.now());
            eventRepository.save(adjustment);
            syncCounter.advanceTo(nextSeq + 1);
            adjustmentEventId = adjustment.getEventId();
        }

        resolutionRepository.save(new ResolutionEntity(request.resolutionId(), discrepancyId, request.outcome(),
                request.reason(), request.quantityDelta(), principal.userId(), principal.deviceId(),
                adjustmentEventId));
        discrepancy.markResolved(request.resolutionId());
        discrepancyRepository.save(discrepancy);

        if (adjustment != null) {
            reconciliationService.reconcile(Set.of(discrepancy.getItemId()), List.of(adjustment));
        }
        log.info("discrepancy resolved id={} outcome={} by={}", discrepancyId, request.outcome(), principal.username());
        return new ResolveResponse(request.resolutionId(), "RESOLVED", adjustmentEventId);
    }
}
