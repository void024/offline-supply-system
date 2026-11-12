package com.offlinesupply.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offlinesupply.server.api.dto.EventDtos.EventInputDto;
import com.offlinesupply.server.api.dto.EventDtos.EventResult;
import com.offlinesupply.server.api.dto.EventDtos.PushEventsRequest;
import com.offlinesupply.server.api.dto.EventDtos.PushEventsResponse;
import com.offlinesupply.server.config.AppProperties;
import com.offlinesupply.server.domain.EventEntity;
import com.offlinesupply.server.domain.EventOrigin;
import com.offlinesupply.server.domain.EventType;
import com.offlinesupply.server.domain.IssueKind;
import com.offlinesupply.server.domain.IssuerDesignationEntity;
import com.offlinesupply.server.domain.PushStatus;
import com.offlinesupply.server.domain.Role;
import com.offlinesupply.server.domain.SubmissionIssueEntity;
import com.offlinesupply.server.exception.ApiException;
import com.offlinesupply.server.repository.EventRepository;
import com.offlinesupply.server.repository.IssuerDesignationRepository;
import com.offlinesupply.server.repository.ItemRepository;
import com.offlinesupply.server.repository.SubmissionIssueRepository;
import com.offlinesupply.server.repository.UserRepository;
import com.offlinesupply.server.security.SessionPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The ONLY write path for movement events (contract endpoint 3). Each event is judged
 * independently: ACCEPTED / DUPLICATE / CONFLICT / QUARANTINED. The whole batch runs in
 * one transaction that first locks sync_counters; only accepted events consume
 * server_seq values, so the ordering stream never contains rejected data.
 */
@Service
public class EventIngestService {

    private static final Logger log = LoggerFactory.getLogger("com.offlinesupply.server.sync");

    private final EventRepository eventRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final IssuerDesignationRepository issuerRepository;
    private final SubmissionIssueRepository submissionIssueRepository;
    private final SyncCounterService syncCounter;
    private final ReconciliationService reconciliationService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public EventIngestService(EventRepository eventRepository, ItemRepository itemRepository,
                              UserRepository userRepository, IssuerDesignationRepository issuerRepository,
                              SubmissionIssueRepository submissionIssueRepository, SyncCounterService syncCounter,
                              ReconciliationService reconciliationService, AppProperties properties,
                              ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.issuerRepository = issuerRepository;
        this.submissionIssueRepository = submissionIssueRepository;
        this.syncCounter = syncCounter;
        this.reconciliationService = reconciliationService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PushEventsResponse push(SessionPrincipal principal, PushEventsRequest request) {
        if (request.events().size() > properties.sync().maxBatchSize()) {
            throw ApiException.badRequest("batch exceeds the 500-event limit");
        }

        long nextSeq = syncCounter.lockAndRead();
        Optional<IssuerDesignationEntity> issuer = issuerRepository.findById(IssuerDesignationEntity.SINGLETON_KEY);

        List<EventResult> results = new ArrayList<>(request.events().size());
        List<EventEntity> accepted = new ArrayList<>();
        Set<UUID> affectedItems = new HashSet<>();

        for (EventInputDto in : request.events()) {
            // 1. Idempotency on the event_id key.
            Optional<EventEntity> existing = eventRepository.findById(in.eventId());
            if (existing.isPresent()) {
                if (samePayload(existing.get(), in, principal.deviceId())) {
                    results.add(new EventResult(in.eventId(), PushStatus.DUPLICATE, null));
                } else {
                    String detail = "same event_id was already submitted with a different payload; nothing was applied";
                    recordIssue(principal.deviceId(), in, IssueKind.PAYLOAD_CONFLICT, detail);
                    results.add(new EventResult(in.eventId(), PushStatus.CONFLICT, detail));
                }
                continue;
            }

            // 2. Device counter reuse with a different event id.
            Optional<EventEntity> sameSeq = eventRepository.findByDeviceIdAndDeviceSeq(
                    principal.deviceId(), in.deviceSeq());
            if (sameSeq.isPresent()) {
                String detail = "device_seq " + in.deviceSeq() + " was already used by event "
                        + sameSeq.get().getEventId() + "; nothing was applied";
                recordIssue(principal.deviceId(), in, IssueKind.SEQ_CONFLICT, detail);
                results.add(new EventResult(in.eventId(), PushStatus.CONFLICT, detail));
                continue;
            }

            // 3. Per-event validation; failures never abort the rest of the batch.
            String validationError = validate(in);
            if (validationError != null) {
                recordIssue(principal.deviceId(), in, IssueKind.VALIDATION_QUARANTINE, validationError);
                results.add(new EventResult(in.eventId(), PushStatus.QUARANTINED, validationError));
                continue;
            }

            // 4. Policy: authorization uses only the authenticated pusher.
            if (in.type() == EventType.ADJUSTMENT && principal.role() != Role.SUPERVISOR) {
                String detail = "ADJUSTMENT requires the pushing session to be a supervisor";
                recordIssue(principal.deviceId(), in, IssueKind.ROLE_QUARANTINE, detail);
                results.add(new EventResult(in.eventId(), PushStatus.QUARANTINED, detail));
                continue;
            }
            if ((in.type() == EventType.PLANNED_ISSUE || in.type() == EventType.HANDOVER)
                    && (issuer.isEmpty() || !issuer.get().getDeviceId().equals(principal.deviceId()))) {
                String detail = issuer.isEmpty()
                        ? "no issuer device is designated; issue-type events are quarantined"
                        : "issue-type events are accepted only from the designated issuer device";
                recordIssue(principal.deviceId(), in, IssueKind.ISSUER_QUARANTINE, detail);
                results.add(new EventResult(in.eventId(), PushStatus.QUARANTINED, detail));
                continue;
            }

            // 5. Accept: assign the next serialized server_seq and persist.
            EventEntity event = new EventEntity(in.eventId(), EventOrigin.DEVICE, nextSeq++, principal.deviceId(),
                    in.deviceSeq(), in.recordedByUserId(), principal.userId(), in.type(), in.itemId(), in.quantity(),
                    in.plannedIssueId(), in.reason(), in.occurredAt(), OffsetDateTime.now());
            eventRepository.save(event);
            accepted.add(event);
            affectedItems.add(event.getItemId());
            results.add(new EventResult(in.eventId(), PushStatus.ACCEPTED, null));
        }

        syncCounter.advanceTo(nextSeq);
        reconciliationService.reconcile(affectedItems, accepted);

        log.info("push device={} batch={} accepted={} rejected={}",
                principal.deviceId(), request.events().size(), accepted.size(),
                request.events().size() - accepted.size());
        return new PushEventsResponse(results);
    }

    /** Duplicate = identical payload under the same event_id. Compares every client-set field. */
    private boolean samePayload(EventEntity stored, EventInputDto in, UUID deviceId) {
        return stored.getOrigin() == EventOrigin.DEVICE
                && deviceId.equals(stored.getDeviceId())
                && in.deviceSeq().equals(stored.getDeviceSeq())
                && in.recordedByUserId().equals(stored.getRecordedByUserId())
                && in.type() == stored.getType()
                && in.itemId().equals(stored.getItemId())
                && in.quantity() == stored.getQuantity()
                && java.util.Objects.equals(in.plannedIssueId(), stored.getPlannedIssueId())
                && java.util.Objects.equals(trimToNull(in.reason()), trimToNull(stored.getReason()))
                // occurred_at is advisory device clock, stored at microsecond precision with
                // rounding; a sub-millisecond tolerance keeps replay detection exact in practice.
                && Math.abs(in.occurredAt().toInstant().toEpochMilli()
                        - stored.getOccurredAt().toInstant().toEpochMilli()) <= 1;
    }

    /** Semantic validation per contract; null means valid. */
    private String validate(EventInputDto in) {
        if (in.deviceSeq() < 1) {
            return "device_seq must be a positive gapless counter";
        }
        if (!itemRepository.existsById(in.itemId())) {
            return "item_id does not reference a known item";
        }
        if (!userRepository.existsById(in.recordedByUserId())) {
            return "recorded_by_user_id does not reference a real user";
        }
        if (in.type() == EventType.ADJUSTMENT) {
            if (in.quantity() == 0) {
                return "quantity must be non-zero for ADJUSTMENT";
            }
            if (in.reason() == null || in.reason().isBlank()) {
                return "reason is required for ADJUSTMENT";
            }
        } else if (in.quantity() <= 0) {
            return "quantity must be > 0 for " + in.type();
        }
        return null;
    }

    /** Quarantine/report: dedupes on (claimed_event_id, kind); a retry returns the same status without a new row. */
    private void recordIssue(UUID deviceId, EventInputDto in, IssueKind kind, String detail) {
        if (submissionIssueRepository.findByClaimedEventIdAndKind(in.eventId(), kind).isPresent()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(in);
        } catch (JsonProcessingException e) {
            payload = "{\"unserializable\": true}";
        }
        submissionIssueRepository.save(new SubmissionIssueEntity(UUID.randomUUID(), deviceId, in.eventId(),
                kind, payload, detail, OffsetDateTime.now()));
        log.info("submission quarantined kind={} event_id={} detail={}", kind, in.eventId(), detail);
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
