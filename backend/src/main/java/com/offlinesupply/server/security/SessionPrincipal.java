package com.offlinesupply.server.security;

import com.offlinesupply.server.domain.Role;

import java.util.UUID;

/** The authenticated caller: the session user and the enrolled device they are on. */
public record SessionPrincipal(UUID userId, String username, Role role, UUID deviceId, String tokenHash) {

    public boolean isSupervisor() {
        return role == Role.SUPERVISOR;
    }
}
