package com.offlinesupply.server.api;

import com.offlinesupply.server.api.dto.DiscrepancyDtos.DiscrepanciesResponse;
import com.offlinesupply.server.api.dto.DiscrepancyDtos.ResolveRequest;
import com.offlinesupply.server.api.dto.DiscrepancyDtos.ResolveResponse;
import com.offlinesupply.server.domain.DiscrepancyStatus;
import com.offlinesupply.server.security.SessionPrincipal;
import com.offlinesupply.server.service.DiscrepancyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract endpoints 7-8. Resolve is supervisor-only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/discrepancies")
public class DiscrepanciesController {

    private final DiscrepancyService discrepancyService;

    public DiscrepanciesController(DiscrepancyService discrepancyService) {
        this.discrepancyService = discrepancyService;
    }

    @GetMapping
    public ResponseEntity<DiscrepanciesResponse> list(@RequestParam(required = false) DiscrepancyStatus status) {
        return ResponseEntity.ok(discrepancyService.list(status));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ResolveResponse> resolve(@AuthenticationPrincipal SessionPrincipal principal,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody ResolveRequest request) {
        return ResponseEntity.ok(discrepancyService.resolve(principal, id, request));
    }
}
