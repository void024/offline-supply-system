package com.offlinesupply.server.repository;

import com.offlinesupply.server.domain.ResolutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResolutionRepository extends JpaRepository<ResolutionEntity, UUID> {
}
