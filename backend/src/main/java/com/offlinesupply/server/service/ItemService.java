package com.offlinesupply.server.service;

import com.offlinesupply.server.api.dto.ItemDtos.ItemDto;
import com.offlinesupply.server.api.dto.ItemDtos.ItemsResponse;
import com.offlinesupply.server.repository.EventRepository;
import com.offlinesupply.server.repository.EventRepository.HandoverAggView;
import com.offlinesupply.server.repository.EventRepository.ItemBalanceView;
import com.offlinesupply.server.repository.EventRepository.PlanTotalView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contract endpoint 5. Balance is computed from the event log on every read and can be
 * negative. planned_outstanding = max(0, planned - linked handovers) clamped PER PLAN
 * and then summed per item, so an over-delivered plan never offsets another plan's
 * remainder (the over-delivery lives in PLAN_EXCEEDED instead).
 */
@Service
public class ItemService {

    private final EventRepository eventRepository;

    public ItemService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public ItemsResponse list() {
        return new ItemsResponse(computeItems());
    }

    /** Used by the sync pull's reference snapshot (inside the pull's snapshot transaction). */
    public List<ItemDto> computeItems() {
        Map<UUID, Long> handedByPlan = new HashMap<>();
        for (HandoverAggView agg : eventRepository.handoverTotals()) {
            handedByPlan.put(agg.getPlanId(), agg.getHanded());
        }
        Map<UUID, Long> outstandingByItem = new HashMap<>();
        for (PlanTotalView plan : eventRepository.planTotals()) {
            long remaining = Math.max(0L, plan.getPlanned() - handedByPlan.getOrDefault(plan.getPlanId(), 0L));
            outstandingByItem.merge(plan.getItemId(), remaining, Long::sum);
        }
        List<ItemDto> items = new ArrayList<>();
        for (ItemBalanceView view : eventRepository.computeBalances()) {
            items.add(new ItemDto(view.getId(), view.getName(), view.getUnit(), view.getBalance(),
                    outstandingByItem.getOrDefault(view.getId(), 0L)));
        }
        return items;
    }
}
