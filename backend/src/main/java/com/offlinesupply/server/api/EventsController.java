package com.offlinesupply.server.api;

import com.offlinesupply.server.api.dto.EventDtos.EventHistoryResponse;
import com.offlinesupply.server.api.dto.EventDtos.PushEventsRequest;
import com.offlinesupply.server.api.dto.EventDtos.PushEventsResponse;
import com.offlinesupply.server.domain.EventType;
import com.offlinesupply.server.security.SessionPrincipal;
import com.offlinesupply.server.service.EventIngestService;
import com.offlinesupply.server.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract endpoints 3 (push - the only write path) and 6 (read-only history browse). */
@RestController
@RequestMapping("/api/v1/events")
public class EventsController {

    private final EventIngestService eventIngestService;
    private final SyncService syncService;

    public EventsController(EventIngestService eventIngestService, SyncService syncService) {
        this.eventIngestService = eventIngestService;
        this.syncService = syncService;
    }

    @PostMapping
    public ResponseEntity<PushEventsResponse> push(@AuthenticationPrincipal SessionPrincipal principal,
                                                   @Valid @RequestBody PushEventsRequest request) {
        return ResponseEntity.ok(eventIngestService.push(principal, request));
    }

    @GetMapping
    public ResponseEntity<EventHistoryResponse> history(@RequestParam(name = "item_id", required = false) UUID itemId,
                                                        @RequestParam(required = false) EventType type,
                                                        @RequestParam(defaultValue = "0") long after,
                                                        @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(syncService.history(itemId, type, after, limit));
    }
}
