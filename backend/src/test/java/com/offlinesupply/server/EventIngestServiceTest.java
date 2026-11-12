package com.offlinesupply.server;

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
import com.offlinesupply.server.repository.EventRepository;
import com.offlinesupply.server.repository.IssuerDesignationRepository;
import com.offlinesupply.server.repository.ItemRepository;
import com.offlinesupply.server.repository.SubmissionIssueRepository;
import com.offlinesupply.server.repository.UserRepository;
import com.offlinesupply.server.security.SessionPrincipal;
import com.offlinesupply.server.service.EventIngestService;
import com.offlinesupply.server.service.ReconciliationService;
import com.offlinesupply.server.service.SyncCounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for per-event ingest judgement, with repositories mocked. These cover the
 * decision branches; the database-backed behavior (constraints, ordering, reconciliation)
 * is covered by SupplyApiIntegrationTest against a real PostgreSQL.
 */
class EventIngestServiceTest {

    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();

    private EventRepository eventRepository;
    private ItemRepository itemRepository;
    private UserRepository userRepository;
    private IssuerDesignationRepository issuerRepository;
    private SubmissionIssueRepository submissionIssueRepository;
    private SyncCounterService syncCounter;
    private ReconciliationService reconciliationService;
    private EventIngestService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        itemRepository = mock(ItemRepository.class);
        userRepository = mock(UserRepository.class);
        issuerRepository = mock(IssuerDesignationRepository.class);
        submissionIssueRepository = mock(SubmissionIssueRepository.class);
        syncCounter = mock(SyncCounterService.class);
        reconciliationService = mock(ReconciliationService.class);
        AppProperties props = new AppProperties(
                new AppProperties.Session("SUPPLY_SESSION", 24, false),
                new AppProperties.Login(5, 60),
                new AppProperties.Sync(500, 500));
        service = new EventIngestService(eventRepository, itemRepository, userRepository, issuerRepository,
                submissionIssueRepository, syncCounter, reconciliationService, props, new ObjectMapper()
                .findAndRegisterModules());

        when(syncCounter.lockAndRead()).thenReturn(1L);
        when(itemRepository.existsById(ITEM)).thenReturn(true);
        when(userRepository.existsById(USER)).thenReturn(true);
        when(submissionIssueRepository.findByClaimedEventIdAndKind(any(), any())).thenReturn(Optional.empty());
    }

    private SessionPrincipal keeper() {
        return new SessionPrincipal(USER, "keeper1", Role.STOREKEEPER, DEVICE, "hash");
    }

    private SessionPrincipal boss() {
        return new SessionPrincipal(USER, "boss1", Role.SUPERVISOR, DEVICE, "hash");
    }

    private EventInputDto receipt(UUID eventId) {
        return new EventInputDto(eventId, 1, USER, EventType.RECEIPT, ITEM, 100, null, null,
                OffsetDateTime.now());
    }

    private EventEntity storedFrom(EventInputDto in, long seq) {
        return new EventEntity(in.eventId(), EventOrigin.DEVICE, seq, DEVICE, in.deviceSeq(),
                in.recordedByUserId(), USER, in.type(), in.itemId(), in.quantity(), in.plannedIssueId(),
                in.reason(), in.occurredAt(), OffsetDateTime.now());
    }

    @Test
    void identicalReplayIsDuplicateAndWritesNothing() {
        EventInputDto in = receipt(UUID.randomUUID());
        when(eventRepository.findById(in.eventId())).thenReturn(Optional.of(storedFrom(in, 1)));

        PushEventsResponse response = service.push(keeper(), new PushEventsRequest(List.of(in)));

        assertEquals(PushStatus.DUPLICATE, response.results().get(0).status());
        verify(eventRepository, never()).save(any(EventEntity.class));
        verify(submissionIssueRepository, never()).save(any());
    }

    @Test
    void sameEventIdDifferentPayloadIsConflictAndQuarantined() {
        EventInputDto in = receipt(UUID.randomUUID());
        EventEntity different = storedFrom(in, 1);
        // stored payload differs in quantity via a second event built from a modified input
        different = storedFrom(new EventInputDto(in.eventId(), in.deviceSeq(), USER, EventType.RECEIPT, ITEM,
                999, null, null, in.occurredAt()), 1);
        when(eventRepository.findById(in.eventId())).thenReturn(Optional.of(different));

        PushEventsResponse response = service.push(keeper(), new PushEventsRequest(List.of(in)));

        assertEquals(PushStatus.CONFLICT, response.results().get(0).status());
        ArgumentCaptor<SubmissionIssueEntity> captor = ArgumentCaptor.forClass(SubmissionIssueEntity.class);
        verify(submissionIssueRepository).save(captor.capture());
        assertEquals(IssueKind.PAYLOAD_CONFLICT, captor.getValue().getKind());
    }

    @Test
    void reusedDeviceSeqWithDifferentEventIdIsConflict() {
        EventInputDto in = receipt(UUID.randomUUID());
        EventEntity priorSeq = storedFrom(new EventInputDto(UUID.randomUUID(), in.deviceSeq(), USER,
                EventType.RECEIPT, ITEM, 5, null, null, in.occurredAt()), 1);
        when(eventRepository.findByDeviceIdAndDeviceSeq(DEVICE, in.deviceSeq())).thenReturn(Optional.of(priorSeq));

        PushEventsResponse response = service.push(keeper(), new PushEventsRequest(List.of(in)));

        assertEquals(PushStatus.CONFLICT, response.results().get(0).status());
        ArgumentCaptor<SubmissionIssueEntity> captor = ArgumentCaptor.forClass(SubmissionIssueEntity.class);
        verify(submissionIssueRepository).save(captor.capture());
        assertEquals(IssueKind.SEQ_CONFLICT, captor.getValue().getKind());
    }

    @Test
    void unknownItemIsQuarantinedWithValidationDetail() {
        EventInputDto in = receipt(UUID.randomUUID());
        when(itemRepository.existsById(ITEM)).thenReturn(false);

        PushEventsResponse response = service.push(keeper(), new PushEventsRequest(List.of(in)));

        EventResult result = response.results().get(0);
        assertEquals(PushStatus.QUARANTINED, result.status());
        assertEquals("item_id does not reference a known item", result.detail());
    }

    @Test
    void storekeeperAdjustmentIsRoleQuarantined() {
        EventInputDto in = new EventInputDto(UUID.randomUUID(), 1, USER, EventType.ADJUSTMENT, ITEM, -5,
                null, "correction", OffsetDateTime.now());

        PushEventsResponse response = service.push(keeper(), new PushEventsRequest(List.of(in)));

        assertEquals(PushStatus.QUARANTINED, response.results().get(0).status());
        ArgumentCaptor<SubmissionIssueEntity> captor = ArgumentCaptor.forClass(SubmissionIssueEntity.class);
        verify(submissionIssueRepository).save(captor.capture());
        assertEquals(IssueKind.ROLE_QUARANTINE, captor.getValue().getKind());
    }

    @Test
    void issueFromNonDesignatedDeviceIsQuarantined() {
        when(issuerRepository.findById(IssuerDesignationEntity.SINGLETON_KEY))
                .thenReturn(Optional.of(new IssuerDesignationEntity(UUID.randomUUID(), USER)));
        EventInputDto in = new EventInputDto(UUID.randomUUID(), 1, USER, EventType.PLANNED_ISSUE, ITEM, 10,
                null, null, OffsetDateTime.now());

        PushEventsResponse response = service.push(keeper(), new PushEventsRequest(List.of(in)));

        assertEquals(PushStatus.QUARANTINED, response.results().get(0).status());
        ArgumentCaptor<SubmissionIssueEntity> captor = ArgumentCaptor.forClass(SubmissionIssueEntity.class);
        verify(submissionIssueRepository).save(captor.capture());
        assertEquals(IssueKind.ISSUER_QUARANTINE, captor.getValue().getKind());
    }

    @Test
    void validEventsConsumeServerSeqInOrder() {
        when(issuerRepository.findById(IssuerDesignationEntity.SINGLETON_KEY))
                .thenReturn(Optional.of(new IssuerDesignationEntity(DEVICE, USER)));
        EventInputDto first = new EventInputDto(UUID.randomUUID(), 1, USER, EventType.RECEIPT, ITEM, 100,
                null, null, OffsetDateTime.now());
        EventInputDto second = new EventInputDto(UUID.randomUUID(), 2, USER, EventType.PLANNED_ISSUE, ITEM, 10,
                null, null, OffsetDateTime.now());

        PushEventsResponse response = service.push(boss(), new PushEventsRequest(List.of(first, second)));

        assertEquals(PushStatus.ACCEPTED, response.results().get(0).status());
        assertEquals(PushStatus.ACCEPTED, response.results().get(1).status());
        ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
        verify(eventRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<EventEntity> saved = captor.getAllValues();
        assertEquals(1L, saved.get(0).getServerSeq());
        assertEquals(2L, saved.get(1).getServerSeq());
        verify(syncCounter).advanceTo(3L);
    }

    @Test
    void adjustmentRequiresReason() {
        EventInputDto in = new EventInputDto(UUID.randomUUID(), 1, USER, EventType.ADJUSTMENT, ITEM, -5,
                null, " ", OffsetDateTime.now());

        PushEventsResponse response = service.push(boss(), new PushEventsRequest(List.of(in)));

        EventResult result = response.results().get(0);
        assertEquals(PushStatus.QUARANTINED, result.status());
        assertEquals("reason is required for ADJUSTMENT", result.detail());
    }

    @Test
    void aFailingEventNeverAbortsTheRestOfTheBatch() {
        EventInputDto bad = receipt(UUID.randomUUID());
        EventInputDto good = receipt(UUID.randomUUID());
        when(itemRepository.existsById(ITEM)).thenReturn(false, true); // first fails validation, second passes

        PushEventsResponse response = service.push(keeper(), new PushEventsRequest(List.of(bad, good)));

        assertEquals(PushStatus.QUARANTINED, response.results().get(0).status());
        assertEquals(PushStatus.ACCEPTED, response.results().get(1).status());
        verify(eventRepository).save(any(EventEntity.class));
        verify(syncCounter).advanceTo(eq(2L));
    }
}
