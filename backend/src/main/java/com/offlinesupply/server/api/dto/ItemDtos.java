package com.offlinesupply.server.api.dto;

import java.util.List;
import java.util.UUID;

/** Contract endpoint 5. Balance is computed on every read and can be negative. */
public final class ItemDtos {

    private ItemDtos() {
    }

    public record ItemDto(UUID id, String name, String unit, long balance, long plannedOutstanding) {
    }

    public record ItemsResponse(List<ItemDto> items) {
    }
}
