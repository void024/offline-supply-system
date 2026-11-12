package com.offlinesupply.server.repository;

import com.offlinesupply.server.domain.IssuerDesignationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuerDesignationRepository extends JpaRepository<IssuerDesignationEntity, Short> {
}
