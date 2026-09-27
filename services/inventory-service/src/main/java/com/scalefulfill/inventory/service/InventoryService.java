package com.scalefulfill.inventory.service;

import com.scalefulfill.inventory.dto.InventoryResponse;
import com.scalefulfill.inventory.dto.ReleaseRequest;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.dto.ReservationResponse;

public interface InventoryService {

    InventoryResponse getInventory(String productId);

    ReservationResponse reserveInventory(ReservationRequest request);

    void releaseInventory(ReleaseRequest request);
}
