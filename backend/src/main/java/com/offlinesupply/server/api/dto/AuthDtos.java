package com.offlinesupply.server.api.dto;

import com.offlinesupply.server.domain.Role;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Contract endpoints 1-2. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    /** device_key is present exactly once, on first enrollment of the device. */
    public record LoginResponse(UUID userId, String username, Role role, String deviceKey) {
    }
}
