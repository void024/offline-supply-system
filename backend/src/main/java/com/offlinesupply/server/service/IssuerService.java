package com.offlinesupply.server.service;

import com.offlinesupply.server.api.dto.IssuerDtos.DesignateResponse;
import com.offlinesupply.server.api.dto.IssuerDtos.IssuerStatusDto;
import com.offlinesupply.server.domain.IssuerDesignationEntity;
import com.offlinesupply.server.exception.ApiException;
import com.offlinesupply.server.repository.DeviceRepository;
import com.offlinesupply.server.repository.IssuerDesignationRepository;
import com.offlinesupply.server.security.SessionPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Contract endpoints 9-10. v1 treats the designation as fixed: one row, ever, enforced
 * by the singleton PK; a concurrent second insert cannot win (409). There is deliberately
 * no re-designate or delete endpoint.
 */
@Service
public class IssuerService {

    private static final Logger log = LoggerFactory.getLogger(IssuerService.class);

    private final IssuerDesignationRepository issuerRepository;
    private final DeviceRepository deviceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public IssuerService(IssuerDesignationRepository issuerRepository, DeviceRepository deviceRepository) {
        this.issuerRepository = issuerRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public IssuerStatusDto status() {
        return issuerRepository.findById(IssuerDesignationEntity.SINGLETON_KEY)
                .map(d -> new IssuerStatusDto(d.getDeviceId(), d.getCreatedAt()))
                .orElse(new IssuerStatusDto(null, null));
    }

    /**
     * Idempotent: re-designating the SAME device returns the existing designation.
     * The insert is atomic (ON CONFLICT DO NOTHING on the singleton PK), so two
     * simultaneous designates decide exactly one winner with no aborted transaction.
     */
    @Transactional
    public DesignateResponse designate(SessionPrincipal principal, UUID deviceId) {
        Optional<IssuerDesignationEntity> existing = issuerRepository.findById(IssuerDesignationEntity.SINGLETON_KEY);
        if (existing.isPresent()) {
            return decide(existing.get(), deviceId);
        }
        if (!deviceRepository.existsById(deviceId)) {
            throw ApiException.badRequest("device_id does not reference an enrolled device");
        }
        int inserted = entityManager.createNativeQuery(
                        "insert into issuer_designations (singleton_key, device_id, designated_by_user_id) "
                                + "values (1, ?1, ?2) on conflict (singleton_key) do nothing")
                .setParameter(1, deviceId)
                .setParameter(2, principal.userId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        IssuerDesignationEntity current = issuerRepository.findById(IssuerDesignationEntity.SINGLETON_KEY)
                .orElseThrow(() -> new IllegalStateException("designation row vanished"));
        if (inserted == 1) {
            log.info("issuer designated device={} by={}", deviceId, principal.username());
        }
        return decide(current, deviceId);
    }

    private DesignateResponse decide(IssuerDesignationEntity current, UUID requestedDeviceId) {
        if (current.getDeviceId().equals(requestedDeviceId)) {
            return new DesignateResponse(current.getDeviceId(), current.getCreatedAt());
        }
        throw ApiException.conflict("an issuer device is already designated (" + current.getDeviceId()
                + "); v1 has no re-designation - see FLAGS");
    }
}
