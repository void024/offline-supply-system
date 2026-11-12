package com.offlinesupply.server.service;

import com.offlinesupply.server.domain.DiscrepancyEntity;
import com.offlinesupply.server.domain.DiscrepancyStatus;
import com.offlinesupply.server.domain.DiscrepancyType;
import com.offlinesupply.server.domain.EventEntity;
import com.offlinesupply.server.domain.EventType;
import com.offlinesupply.server.domain.ItemEntity;
import com.offlinesupply.server.repository.DiscrepancyRepository;
import com.offlinesupply.server.repository.EventRepository;
import com.offlinesupply.server.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciliation rules R1-R4, re-evaluated over affected items/plans after every
 * accepted batch, inside the same transaction that wrote the events.
 *
 * No reopen loops: a row's evidence_seq records the highest server_seq of the facts it
 * covers; a new row for the same (type, evidence_key) opens only when relevant events
 * with a higher server_seq have arrived. R1-R3 close only through the resolve endpoint;
 * R4 auto-closes only when the referenced event arrives AND is a PLANNED_ISSUE for the
 * same item.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final DiscrepancyRepository discrepancyRepository;
    private final EventRepository eventRepository;
    private final ItemRepository itemRepository;

    public ReconciliationService(DiscrepancyRepository discrepancyRepository,
                                 EventRepository eventRepository, ItemRepository itemRepository) {
        this.discrepancyRepository = discrepancyRepository;
        this.eventRepository = eventRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * @param affectedItemIds items touched by newly accepted events
     * @param acceptedEvents  the newly accepted events themselves (R2/R3/R4 evidence)
     */
    public void reconcile(Collection<UUID> affectedItemIds, List<EventEntity> acceptedEvents) {
        for (UUID itemId : affectedItemIds) {
            evaluateNegativeBalance(itemId);
        }
        for (EventEntity event : acceptedEvents) {
            if (event.getType() == EventType.HANDOVER && event.getPlannedIssueId() == null) {
                flagUnplannedHandover(event);
            }
            if (event.getType() == EventType.HANDOVER && event.getPlannedIssueId() != null) {
                evaluateReference(event);
                evaluatePlanExceeded(event.getPlannedIssueId());
            }
            if (event.getType() == EventType.PLANNED_ISSUE) {
                closeResolvedReferences(event);
                evaluatePlanExceeded(event.getEventId());
            }
        }
    }

    /** R1: balance below zero -> OPEN row. Never auto-closes. */
    private void evaluateNegativeBalance(UUID itemId) {
        ItemEntity item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return;
        }
        long balance = item.getOpeningQty() + eventRepository.movementSumForItem(itemId);
        if (balance >= 0) {
            return; // R1 never auto-closes; an existing OPEN row stays for human review.
        }
        long maxSeq = eventRepository.maxSeqForItem(itemId);
        String details = "Accounting balance is " + balance + " for " + item.getName()
                + ". A delayed receipt or a factual over-handover can explain this; the facts are retained.";
        openOrRefresh(DiscrepancyType.NEGATIVE_BALANCE, itemId, itemId.toString(), maxSeq, details);
    }

    /** R2: handover with no planned_issue_id -> soft flag; the handover stays a recorded fact. */
    private void flagUnplannedHandover(EventEntity handover) {
        String evidenceKey = handover.getEventId().toString();
        if (!discrepancyRepository.findByTypeAndEvidenceKey(DiscrepancyType.UNPLANNED_HANDOVER, evidenceKey)
                .isEmpty()) {
            return; // already recorded (open or reviewed); the fact itself never changes.
        }
        String itemName = itemRepository.findById(handover.getItemId()).map(ItemEntity::getName).orElse("unknown item");
        String details = "Handover " + handover.getEventId() + " of " + handover.getQuantity()
                + " unit(s) of " + itemName + " has no planned issue linked. The fact is retained; a supervisor reviews it.";
        open(DiscrepancyType.UNPLANNED_HANDOVER, handover.getItemId(), evidenceKey, handover.getServerSeq(), details);
    }

    /**
     * R3: linked handovers total more than planned -> flag with planned vs handed vs excess.
     * The displayed planned_outstanding clamps at zero; the excess lives here.
     */
    private void evaluatePlanExceeded(UUID planEventId) {
        Optional<EventEntity> planOpt = eventRepository.findById(planEventId);
        if (planOpt.isEmpty() || planOpt.get().getType() != EventType.PLANNED_ISSUE) {
            return; // unresolved references are R4's job.
        }
        EventEntity plan = planOpt.get();
        List<EventEntity> handovers = eventRepository
                .findByTypeAndPlannedIssueIdOrderByServerSeqAsc(EventType.HANDOVER, planEventId);
        long handed = handovers.stream().mapToLong(EventEntity::getQuantity).sum();
        if (handed <= plan.getQuantity()) {
            return; // within plan; R3 does not auto-close either.
        }
        long maxSeq = handovers.stream().mapToLong(EventEntity::getServerSeq).max().orElse(plan.getServerSeq());
        String itemName = itemRepository.findById(plan.getItemId()).map(ItemEntity::getName).orElse("unknown item");
        String details = "Planned issue " + planEventId + " for " + itemName + " planned " + plan.getQuantity()
                + " but handovers linked to it total " + handed + " - excess " + (handed - plan.getQuantity())
                + ". planned_outstanding clamps at zero; the excess is surfaced here.";
        openOrRefresh(DiscrepancyType.PLAN_EXCEEDED, plan.getItemId(), planEventId.toString(), maxSeq, details);
    }

    /**
     * R4 (on the handover side): planned_issue_id points to an unseen event, or to the
     * wrong type/item -> flag and keep OPEN. Wrong type or wrong item NEVER auto-closes.
     */
    private void evaluateReference(EventEntity handover) {
        UUID ref = handover.getPlannedIssueId();
        Optional<EventEntity> refOpt = eventRepository.findById(ref);
        boolean resolves = refOpt.isPresent()
                && refOpt.get().getType() == EventType.PLANNED_ISSUE
                && refOpt.get().getItemId().equals(handover.getItemId());
        if (resolves) {
            autoClose(DiscrepancyType.UNRESOLVED_REFERENCE, handover.getEventId().toString());
            return;
        }
        String evidenceKey = handover.getEventId().toString();
        if (!discrepancyRepository.findByTypeAndEvidenceKey(DiscrepancyType.UNRESOLVED_REFERENCE, evidenceKey)
                .isEmpty()) {
            return;
        }
        String detail;
        if (refOpt.isEmpty()) {
            detail = "Handover " + handover.getEventId() + " references planned issue " + ref
                    + " which has not been seen. If it arrives later as a PLANNED_ISSUE for the same item, this closes automatically.";
        } else if (refOpt.get().getType() != EventType.PLANNED_ISSUE) {
            detail = "Handover " + handover.getEventId() + " references event " + ref
                    + " which is a " + refOpt.get().getType() + ", not a PLANNED_ISSUE. Stays open for human review.";
        } else {
            detail = "Handover " + handover.getEventId() + " references plan " + ref
                    + " which belongs to a different item. Stays open for human review.";
        }
        open(DiscrepancyType.UNRESOLVED_REFERENCE, handover.getItemId(), evidenceKey, handover.getServerSeq(), detail);
    }

    /**
     * R4 (on the plan side): when a PLANNED_ISSUE arrives, auto-close OPEN reference flags
     * of handovers pointing at it, only for the same item. Auto-close writes no resolution
     * row - resolved_by_resolution_id stays NULL, marking it system-closed.
     */
    private void closeResolvedReferences(EventEntity plan) {
        List<EventEntity> handovers = eventRepository
                .findByTypeAndPlannedIssueIdOrderByServerSeqAsc(EventType.HANDOVER, plan.getEventId());
        for (EventEntity handover : handovers) {
            if (handover.getItemId().equals(plan.getItemId())) {
                autoClose(DiscrepancyType.UNRESOLVED_REFERENCE, handover.getEventId().toString());
            }
        }
    }

    /** Open a new row, or refresh the OPEN one; never reopen facts already reviewed. */
    private void openOrRefresh(DiscrepancyType type, UUID itemId, String evidenceKey, long maxSeq, String details) {
        Optional<DiscrepancyEntity> open = discrepancyRepository
                .findByTypeAndEvidenceKeyAndStatus(type, evidenceKey, DiscrepancyStatus.OPEN);
        if (open.isPresent()) {
            DiscrepancyEntity existing = open.get();
            if (maxSeq > existing.getEvidenceSeq()) {
                existing.updateEvidence(maxSeq, details);
                discrepancyRepository.save(existing);
                log.info("discrepancy refreshed type={} evidence_key={} evidence_seq={}", type, evidenceKey, maxSeq);
            }
            return;
        }
        long maxReviewedSeq = discrepancyRepository.findByTypeAndEvidenceKey(type, evidenceKey).stream()
                .filter(d -> d.getStatus() == DiscrepancyStatus.RESOLVED)
                .mapToLong(DiscrepancyEntity::getEvidenceSeq)
                .max().orElse(-1L);
        if (maxReviewedSeq >= maxSeq) {
            return; // a supervisor already reviewed exactly these facts.
        }
        open(type, itemId, evidenceKey, maxSeq, details);
    }

    private void open(DiscrepancyType type, UUID itemId, String evidenceKey, long evidenceSeq, String details) {
        discrepancyRepository.save(new DiscrepancyEntity(UUID.randomUUID(), itemId, type, evidenceKey,
                evidenceSeq, details, DiscrepancyStatus.OPEN, null, OffsetDateTime.now()));
        log.info("discrepancy opened type={} evidence_key={} evidence_seq={}", type, evidenceKey, evidenceSeq);
    }

    private void autoClose(DiscrepancyType type, String evidenceKey) {
        Optional<DiscrepancyEntity> open = discrepancyRepository
                .findByTypeAndEvidenceKeyAndStatus(type, evidenceKey, DiscrepancyStatus.OPEN);
        if (open.isPresent()) {
            open.get().markResolved(null);
            discrepancyRepository.save(open.get());
            log.info("discrepancy auto-closed type={} evidence_key={}", type, evidenceKey);
        }
    }
}
