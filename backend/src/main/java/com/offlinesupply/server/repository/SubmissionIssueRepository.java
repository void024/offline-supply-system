package com.offlinesupply.server.repository;

import com.offlinesupply.server.domain.IssueKind;
import com.offlinesupply.server.domain.SubmissionIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubmissionIssueRepository extends JpaRepository<SubmissionIssueEntity, UUID> {

    Optional<SubmissionIssueEntity> findByClaimedEventIdAndKind(UUID claimedEventId, IssueKind kind);
}
