package com.offlinesupply.server.api;

import com.offlinesupply.server.api.dto.SyncDtos.PullResponse;
import com.offlinesupply.server.security.SessionPrincipal;
import com.offlinesupply.server.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contract endpoint 4. */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/pull")
    public ResponseEntity<PullResponse> pull(@AuthenticationPrincipal SessionPrincipal principal,
                                             @RequestParam(defaultValue = "0") long after,
                                             @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(syncService.pull(principal, after, limit));
    }
}
