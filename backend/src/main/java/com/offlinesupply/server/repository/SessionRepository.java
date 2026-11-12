package com.offlinesupply.server.repository;

import com.offlinesupply.server.domain.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    Optional<SessionEntity> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
