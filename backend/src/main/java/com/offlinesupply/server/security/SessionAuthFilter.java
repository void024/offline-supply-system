package com.offlinesupply.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offlinesupply.server.config.AppProperties;
import com.offlinesupply.server.domain.DeviceEntity;
import com.offlinesupply.server.domain.SessionEntity;
import com.offlinesupply.server.domain.UserEntity;
import com.offlinesupply.server.repository.DeviceRepository;
import com.offlinesupply.server.repository.SessionRepository;
import com.offlinesupply.server.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Session-cookie + device-header authentication (Prompt 2 contract):
 * the cookie identifies the session; X-Device-Id + X-Device-Key must match the
 * enrolled device the session was created on. Login is the only open route.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthFilter.class);

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SessionAuthFilter(SessionRepository sessionRepository, UserRepository userRepository,
                             DeviceRepository deviceRepository, AppProperties properties,
                             ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.equals("/api/v1/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request, properties.session().cookieName());
        if (token == null || token.isBlank()) {
            reject(response, 401, "UNAUTHORIZED", "missing session cookie");
            return;
        }
        Optional<SessionEntity> sessionOpt = sessionRepository.findByTokenHash(Hashing.sha256Hex(token));
        if (sessionOpt.isEmpty() || sessionOpt.get().getExpiresAt().isBefore(OffsetDateTime.now())) {
            reject(response, 401, "UNAUTHORIZED", "session is invalid or expired");
            return;
        }
        SessionEntity session = sessionOpt.get();

        String deviceIdHeader = request.getHeader("X-Device-Id");
        String deviceKeyHeader = request.getHeader("X-Device-Key");
        if (deviceIdHeader == null || !deviceIdHeader.equalsIgnoreCase(session.getDeviceId().toString())) {
            reject(response, 403, "FORBIDDEN", "device id does not match the session");
            return;
        }
        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(session.getDeviceId());
        if (deviceOpt.isEmpty() || deviceKeyHeader == null
                || !deviceOpt.get().getDeviceKeyHash().equals(Hashing.sha256Hex(deviceKeyHeader))) {
            reject(response, 403, "FORBIDDEN", "device key missing or invalid for this session");
            return;
        }

        UserEntity user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null) {
            reject(response, 401, "UNAUTHORIZED", "session user no longer exists");
            return;
        }

        SessionPrincipal principal = new SessionPrincipal(user.getId(), user.getUsername(), user.getRole(),
                session.getDeviceId(), session.getTokenHash());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MDC.put("actor", user.getUsername());
        MDC.put("device_id", session.getDeviceId().toString());
        MDC.put("endpoint", request.getMethod() + " " + request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("actor");
            MDC.remove("device_id");
            MDC.remove("endpoint");
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        log.debug("rejecting request: {} {}", status, message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", code);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
