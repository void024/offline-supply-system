package com.offlinesupply.server.api;

import com.offlinesupply.server.api.dto.AuthDtos.LoginRequest;
import com.offlinesupply.server.api.dto.AuthDtos.LoginResponse;
import com.offlinesupply.server.security.SessionPrincipal;
import com.offlinesupply.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract endpoints 1-2. Login is open (rate limited); logout needs session + device. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               @RequestHeader(value = "X-Device-Id", required = false)
                                               String deviceId,
                                               @RequestHeader(value = "X-Device-Key", required = false)
                                               String deviceKey,
                                               HttpServletRequest httpRequest) {
        AuthService.LoginOutcome outcome = authService.login(httpRequest.getRemoteAddr(), request, deviceId, deviceKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, outcome.sessionCookie())
                .body(outcome.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal SessionPrincipal principal) {
        String clearedCookie = authService.logout(principal == null ? null : principal.tokenHash());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedCookie)
                .build();
    }
}
