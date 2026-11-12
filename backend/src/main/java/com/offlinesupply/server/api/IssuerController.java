package com.offlinesupply.server.api;

import com.offlinesupply.server.api.dto.IssuerDtos.DesignateRequest;
import com.offlinesupply.server.api.dto.IssuerDtos.DesignateResponse;
import com.offlinesupply.server.api.dto.IssuerDtos.IssuerStatusDto;
import com.offlinesupply.server.security.SessionPrincipal;
import com.offlinesupply.server.service.IssuerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract endpoints 9-10. Designate is supervisor-only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/issuer")
public class IssuerController {

    private final IssuerService issuerService;

    public IssuerController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    @GetMapping
    public ResponseEntity<IssuerStatusDto> status() {
        return ResponseEntity.ok(issuerService.status());
    }

    @PostMapping("/designate")
    public ResponseEntity<DesignateResponse> designate(@AuthenticationPrincipal SessionPrincipal principal,
                                                       @Valid @RequestBody DesignateRequest request) {
        return ResponseEntity.ok(issuerService.designate(principal, request.deviceId()));
    }
}
