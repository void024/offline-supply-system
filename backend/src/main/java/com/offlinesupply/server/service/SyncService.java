package com.offlinesupply.server.service;

import com.offlinesupply.server.api.dto.DiscrepancyDtos.DiscrepancyDto;
import com.offlinesupply.server.api.dto.EventDtos.EventHistoryResponse;
import com.offlinesupply.server.api.dto.EventDtos.SupplyEventDto;
import com.offlinesupply.server.api.dto.SyncDtos.PullResponse;
import com.offlinesupply.server.api.dto.SyncDtos.ReferenceSnapshot;
import com.offlinesupply.server.api.dto.SyncDtos.SubmissionIssueDto;
import com.offlinesupply.server.config.AppProperties;
import com.offlinesupply.server.domain.EventEntity;
import com.offlinesupply.server.domain.EventType;
import com.offlinesupply.server.domain.SubmissionIssueEntity;
import com.offlinesupply.server.exception.ApiException;
import com.offlinesupply.server.repository.DiscrepancyRepository;
import com.offlinesupply.server.repository.EventRepository;
import com.offlinesupply.server.repository.SubmissionIssueRepository;
import com.offlinesupply.server.security.SessionPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Contract endpoints 4 (pull) and 6 (history browse).
 *
 * Every pull runs in ONE repeatable-read transaction, so the page and the reference
 * snapshot never contradict each other. reference_as_of is the snapshot's high
 * server_seq; the client applies the snapshot only when has_more is false.
 * Cursor rule: next_cursor = the server_seq of the LAST event in this page, never the
 * table maximum; on an empty page it equals the cursor the client sent.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger("com.offlinesupply.server.sync");

    private final EventRepository eventRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final SubmissionIssueRepository submissionIssueRepository;
    private final ItemService itemService;
    private final IssuerService issuerService;
    private final DiscrepancyService discrepancyService;
    private final AppProperties properties;

    public SyncService(EventRepository eventRepository, DiscrepancyRepository discrepancyRepository,
                       SubmissionIssueRepository submissionIssueRepository, ItemService itemService,
                       IssuerService issuerService, DiscrepancyService discrepancyService,
                       AppProperties properties) {
        this.eventRepository = eventRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.submissionIssueRepository = submissionIssueRepository;
        this.itemService = itemService;
        this.issuerService = issuerService;
        this.discrepancyService = discrepancyService;
        this.properties = properties;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public PullResponse pull(SessionPrincipal principal, long after, Integer limit) {
        if (after < 0) {
            throw ApiException.badRequest("after must be >= 0");
        }
        int pageSize = limit == null ? properties.sync().maxPullLimit() : limit;
        if (pageSize < 1 || pageSize > properties.sync().maxPullLimit()) {
            throw ApiException.badRequest("limit must be between 1 and " + properties.sync().maxPullLimit());
        }

        List<EventEntity> fetched = eventRepository.findByServerSeqGreaterThanOrderByServerSeqAsc(
                after, PageRequest.of(0, pageSize + 1));
        boolean hasMore = fetched.size() > pageSize;
        List<EventEntity> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        long nextCursor = page.isEmpty() ? after : page.get(page.size() - 1).getServerSeq();

        List<DiscrepancyDto> discrepancies = discrepancyService.toDtos(
                discrepancyRepository.findAllByOrderByDetectedAtAsc());
        List<SubmissionIssueDto> issues = new ArrayList<>();
        for (SubmissionIssueEntity issue : submissionIssueRepository.findAll()) {
            issues.add(new SubmissionIssueDto(issue.getClaimedEventId(), issue.getKind().name(), issue.getDetail()));
        }
        ReferenceSnapshot reference = new ReferenceSnapshot(itemService.computeItems(), issuerService.status(),
                discrepancies, issues);
        long referenceAsOf = eventRepository.maxServerSeq();

        List<SupplyEventDto> events = new ArrayList<>(page.size());
        for (EventEntity event : page) {
            events.add(toDto(event));
        }
        log.info("pull device={} after={} returned={} has_more={} reference_as_of={}",
                principal.deviceId(), after, events.size(), hasMore, referenceAsOf);
        return new PullResponse(events, nextCursor, hasMore, reference, referenceAsOf);
    }

    @Transactional(readOnly = true)
    public EventHistoryResponse history(UUID itemId, EventType type, long after, Integer limit) {
        if (after < 0) {
            throw ApiException.badRequest("after must be >= 0");
        }
        int pageSize = limit == null ? 100 : limit;
        if (pageSize < 1 || pageSize > properties.sync().maxPullLimit()) {
            throw ApiException.badRequest("limit must be between 1 and " + properties.sync().maxPullLimit());
        }
        List<SupplyEventDto> events = new ArrayList<>();
        for (EventEntity event : eventRepository.history(itemId, type, after, PageRequest.of(0, pageSize))) {
            events.add(toDto(event));
        }
        return new EventHistoryResponse(events);
    }

    public static SupplyEventDto toDto(EventEntity e) {
        return new SupplyEventDto(e.getEventId(), e.getDeviceSeq(), e.getRecordedByUserId(), e.getType(),
                e.getItemId(), e.getQuantity(), e.getPlannedIssueId(), e.getReason(), e.getOccurredAt(),
                e.getServerSeq(), e.getOrigin(), e.getReceivedAt());
    }

}
