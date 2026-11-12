package com.offlinesupply.server.repository;

import com.offlinesupply.server.domain.DiscrepancyEntity;
import com.offlinesupply.server.domain.DiscrepancyStatus;
import com.offlinesupply.server.domain.DiscrepancyType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscrepancyRepository extends JpaRepository<DiscrepancyEntity, UUID> {

    /** Serializes concurrent resolves on the same row: the loser waits, then replays the idempotent outcome. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiscrepancyEntity d where d.id = :id")
    Optional<DiscrepancyEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<DiscrepancyEntity> findByTypeAndEvidenceKeyAndStatus(DiscrepancyType type, String evidenceKey,
                                                                  DiscrepancyStatus status);

    List<DiscrepancyEntity> findByTypeAndEvidenceKey(DiscrepancyType type, String evidenceKey);

    List<DiscrepancyEntity> findByStatusOrderByDetectedAtAsc(DiscrepancyStatus status);

    List<DiscrepancyEntity> findAllByOrderByDetectedAtAsc();
}
