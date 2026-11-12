package com.offlinesupply.server.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * The serialized ordering authority (contract: "all ledger writes are serialized").
 * Every transaction that writes events first locks the single sync_counters row with
 * SELECT ... FOR UPDATE, assigns server_seq values in order, advances the counter, and
 * commits while still holding the lock: lock order = sequence order = commit order.
 * Plain PostgreSQL sequences are deliberately not used - they allocate before commit
 * and can expose a later value before an earlier one commits, which a cursor can skip.
 */
@Component
public class SyncCounterService {

    @PersistenceContext
    private EntityManager entityManager;

    /** Locks the singleton row FOR UPDATE (inside the caller's transaction) and reads it. */
    public long lockAndRead() {
        return ((Number) entityManager
                .createNativeQuery("select next_value from sync_counters where singleton_key = 1 for update")
                .getSingleResult()).longValue();
    }

    /** Advances the counter; the row lock is held until the caller's transaction commits. */
    public void advanceTo(long nextValue) {
        entityManager.createNativeQuery(
                        "update sync_counters set next_value = ?1, updated_at = now() where singleton_key = 1")
                .setParameter(1, nextValue)
                .executeUpdate();
    }
}
