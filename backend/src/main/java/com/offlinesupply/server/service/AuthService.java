package com.offlinesupply.server.service;

import com.offlinesupply.server.api.dto.AuthDtos.LoginRequest;
import com.offlinesupply.server.api.dto.AuthDtos.LoginResponse;
import com.offlinesupply.server.config.AppProperties;
import com.offlinesupply.server.domain.DeviceEntity;
import com.offlinesupply.server.domain.SessionEntity;
import com.offlinesupply.server.domain.UserEntity;
import com.offlinesupply.server.exception.ApiException;
import com.offlinesupply.server.repository.DeviceRepository;
import com.offlinesupply.server.repository.SessionRepository;
import com.offlinesupply.server.repository.UserRepository;
import com.offlinesupply.server.security.Hashing;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Login/logout with device enrollment (contract endpoint 1-2).
 * First login from an unknown X-Device-Id with valid user credentials registers the
 * device and returns a freshly generated device_key exactly once; every later request
 * must present it. Passwords are BCrypt(12); the stored device key is SHA-256 hashed.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository, DeviceRepository deviceRepository,
                       SessionRepository sessionRepository, PasswordEncoder passwordEncoder,
                       LoginRateLimiter rateLimiter, AppProperties properties) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public record LoginOutcome(LoginResponse body, String sessionCookie) {
    }

    @Transactional
    public LoginOutcome login(String clientKey, LoginRequest request, String deviceIdHeader,
                              String deviceKeyHeader) {
        rateLimiter.checkAllowed(clientKey);

        if (deviceIdHeader == null || deviceIdHeader.isBlank()) {
            throw ApiException.badRequest("X-Device-Id header is required at login");
        }
        UUID deviceId;
        try {
            deviceId = UUID.fromString(deviceIdHeader);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("X-Device-Id must be a UUID");
        }

        Optional<UserEntity> userOpt = userRepository.findByUsername(request.username());
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().getPasswordHash())) {
            rateLimiter.recordFailure(clientKey);
            throw ApiException.unauthorized("invalid username or password");
        }
        UserEntity user = userOpt.get();

        String issuedDeviceKey = null;
        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            // Enrollment: issue the device_key exactly once.
            issuedDeviceKey = Hashing.newToken();
            deviceRepository.save(new DeviceEntity(deviceId, null, Hashing.sha256Hex(issuedDeviceKey), user.getId()));
        } else {
            if (deviceKeyHeader == null
                    || !deviceOpt.get().getDeviceKeyHash().equals(Hashing.sha256Hex(deviceKeyHeader))) {
                throw ApiException.forbidden("device key missing or invalid for this enrolled device");
            }
        }

        String token = Hashing.newToken();
        String tokenHash = Hashing.sha256Hex(token);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(properties.session().ttlHours());
        sessionRepository.save(new SessionEntity(UUID.randomUUID(), tokenHash, user.getId(), deviceId, expiresAt));

        ResponseCookie cookie = ResponseCookie.from(properties.session().cookieName(), token)
                .httpOnly(true)
                .secure(properties.session().secureCookie())
                .sameSite("Strict")
                .path("/api")
                .maxAge(Duration.ofHours(properties.session().ttlHours()))
                .build();

        rateLimiter.clear(clientKey);
        LoginResponse body = new LoginResponse(user.getId(), user.getUsername(), user.getRole(), issuedDeviceKey);
        return new LoginOutcome(body, cookie.toString());
    }

    /** Idempotent by contract: 200 always, whether or not the session still exists. */
    @Transactional
    public String logout(String tokenHash) {
        if (tokenHash != null) {
            sessionRepository.deleteByTokenHash(tokenHash);
        }
        ResponseCookie cleared = ResponseCookie.from(properties.session().cookieName(), "")
                .httpOnly(true)
                .secure(properties.session().secureCookie())
                .sameSite("Strict")
                .path("/api")
                .maxAge(0)
                .build();
        return cleared.toString();
    }
}
