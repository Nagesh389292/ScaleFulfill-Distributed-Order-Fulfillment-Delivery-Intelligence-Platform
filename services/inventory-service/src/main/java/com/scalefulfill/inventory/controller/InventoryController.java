package com.scalefulfill.inventory.controller;

import com.scalefulfill.inventory.dto.InventoryResponse;
import com.scalefulfill.inventory.dto.ReleaseRequest;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.dto.ReservationResponse;
import com.scalefulfill.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getInventory(productId));
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserveInventory(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = inventoryService.reserveInventory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/releases")
    public ResponseEntity<Void> releaseInventory(@Valid @RequestBody ReleaseRequest request) {
        inventoryService.releaseInventory(request);
        return ResponseEntity.ok().build();
    }
}
