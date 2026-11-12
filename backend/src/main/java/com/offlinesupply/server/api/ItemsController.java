package com.offlinesupply.server.api;

import com.offlinesupply.server.api.dto.ItemDtos.ItemsResponse;
import com.offlinesupply.server.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract endpoint 5. */
@RestController
@RequestMapping("/api/v1/items")
public class ItemsController {

    private final ItemService itemService;

    public ItemsController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ResponseEntity<ItemsResponse> list() {
        return ResponseEntity.ok(itemService.list());
    }
}
