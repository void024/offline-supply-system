package com.offlinesupply.server.repository;

import com.offlinesupply.server.domain.EventEntity;
import com.offlinesupply.server.domain.EventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    Optional<EventEntity> findByDeviceIdAndDeviceSeq(UUID deviceId, Integer deviceSeq);

    List<EventEntity> findByServerSeqGreaterThanOrderByServerSeqAsc(long after, Pageable pageable);

    @Query("select coalesce(max(e.serverSeq), 0) from EventEntity e")
    long maxServerSeq();

    @Query("select e from EventEntity e "
            + "where (:itemId is null or e.itemId = :itemId) "
            + "and (:type is null or e.type = :type) "
            + "and e.serverSeq > :after order by e.serverSeq asc")
    List<EventEntity> history(@Param("itemId") UUID itemId, @Param("type") EventType type,
                              @Param("after") long after, Pageable pageable);

    /** Computed accounting balance per item: opening + receipts - handovers + adjustments. Never clamped. */
    @Query(value = "select i.id as id, i.name as name, i.unit as unit, "
            + "(i.opening_qty + coalesce(sum(case e.type "
            + "  when 'RECEIPT' then e.quantity "
            + "  when 'HANDOVER' then -e.quantity "
            + "  when 'ADJUSTMENT' then e.quantity else 0 end), 0)) as balance "
            + "from items i left join events e on e.item_id = i.id "
            + "group by i.id, i.name, i.unit, i.opening_qty order by i.name", nativeQuery = true)
    List<ItemBalanceView> computeBalances();

    /** All planned issues (plan id, item, planned quantity) for outstanding/exceeded computations. */
    @Query(value = "select event_id as planId, item_id as itemId, quantity as planned "
            + "from events where type = 'PLANNED_ISSUE'", nativeQuery = true)
    List<PlanTotalView> planTotals();

    /** Linked handover totals per plan, with the highest contributing server_seq (R3 evidence). */
    @Query(value = "select planned_issue_id as planId, sum(quantity) as handed, max(server_seq) as maxSeq "
            + "from events where type = 'HANDOVER' and planned_issue_id is not null "
            + "group by planned_issue_id", nativeQuery = true)
    List<HandoverAggView> handoverTotals();

    @Query(value = "select coalesce(sum(case type "
            + "  when 'RECEIPT' then quantity "
            + "  when 'HANDOVER' then -quantity "
            + "  when 'ADJUSTMENT' then quantity else 0 end), 0) "
            + "from events where item_id = :itemId", nativeQuery = true)
    long movementSumForItem(@Param("itemId") UUID itemId);

    @Query(value = "select coalesce(max(server_seq), 0) from events where item_id = :itemId", nativeQuery = true)
    long maxSeqForItem(@Param("itemId") UUID itemId);

    /** Handovers linked to a specific plan, ascending (R3/R4 evidence). */
    List<EventEntity> findByTypeAndPlannedIssueIdOrderByServerSeqAsc(EventType type, UUID plannedIssueId);

    List<EventEntity> findByTypeAndPlannedIssueIdIn(EventType type, Collection<UUID> plannedIssueIds);

    interface ItemBalanceView {
        UUID getId();

        String getName();

        String getUnit();

        long getBalance();
    }

    interface PlanTotalView {
        UUID getPlanId();

        UUID getItemId();

        long getPlanned();
    }

    interface HandoverAggView {
        UUID getPlanId();

        long getHanded();

        long getMaxSeq();
    }
}
