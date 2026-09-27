package com.scalefulfill.inventory.service;

import com.scalefulfill.inventory.dto.InventoryResponse;
import com.scalefulfill.inventory.dto.ReleaseRequest;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.dto.ReservationResponse;
import com.scalefulfill.inventory.entity.FulfillmentCenter;
import com.scalefulfill.inventory.entity.Inventory;
import com.scalefulfill.inventory.entity.Product;
import com.scalefulfill.inventory.exception.InsufficientInventoryException;
import com.scalefulfill.inventory.exception.ResourceNotFoundException;
import com.scalefulfill.inventory.repository.FulfillmentCenterRepository;
import com.scalefulfill.inventory.repository.InventoryRepository;
import com.scalefulfill.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final FulfillmentCenterRepository fulfillmentCenterRepository;

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        List<Inventory> inventoryList = inventoryRepository.findByProductId(productId);
        Map<String, FulfillmentCenter> fcMap = fulfillmentCenterRepository.findAll().stream()
                .collect(Collectors.toMap(FulfillmentCenter::getId, fc -> fc));

        int totalAvailable = inventoryList.stream().mapToInt(Inventory::getAvailableQuantity).sum();
        int totalReserved = inventoryList.stream().mapToInt(Inventory::getReservedQuantity).sum();

        List<InventoryResponse.FcInventoryDetail> centerDetails = inventoryList.stream().map(inv -> {
            FulfillmentCenter fc = fcMap.get(inv.getFulfillmentCenterId());
            return InventoryResponse.FcInventoryDetail.builder()
                    .fulfillmentCenterId(inv.getFulfillmentCenterId())
                    .fulfillmentCenterCode(fc != null ? fc.getCode() : "UNKNOWN")
                    .fulfillmentCenterName(fc != null ? fc.getName() : "Unknown Center")
                    .availableQuantity(inv.getAvailableQuantity())
                    .reservedQuantity(inv.getReservedQuantity())
                    .build();
        }).collect(Collectors.toList());

        return InventoryResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .totalAvailableQuantity(totalAvailable)
                .totalReservedQuantity(totalReserved)
                .centerDetails(centerDetails)
                .build();
    }

    @Override
    @Transactional
    public ReservationResponse reserveInventory(ReservationRequest request) {
        String productId = request.getProductId();
        int requestedQty = request.getQuantity();

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        Inventory inventoryToReserve;

        if (request.getFulfillmentCenterId() != null && !request.getFulfillmentCenterId().isBlank()) {
            inventoryToReserve = inventoryRepository.findByProductIdAndFulfillmentCenterId(productId, request.getFulfillmentCenterId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format("No inventory record for product [%s] at FC [%s]", productId, request.getFulfillmentCenterId())
                    ));

            if (inventoryToReserve.getAvailableQuantity() < requestedQty) {
                throw new InsufficientInventoryException(
                        String.format("Insufficient inventory for product [%s] at FC [%s]. Requested: %d, Available: %d",
                                productId, request.getFulfillmentCenterId(), requestedQty, inventoryToReserve.getAvailableQuantity())
                );
            }
        } else {
            List<Inventory> availableInventories = inventoryRepository.findAvailableByProductIdOrdered(productId);
            inventoryToReserve = availableInventories.stream()
                    .filter(inv -> inv.getAvailableQuantity() >= requestedQty)
                    .findFirst()
                    .orElseThrow(() -> {
                        int totalAvailable = availableInventories.stream().mapToInt(Inventory::getAvailableQuantity).sum();
                        return new InsufficientInventoryException(
                                String.format("Insufficient inventory across all centers for product [%s]. Requested: %d, Total Available: %d",
                                        productId, requestedQty, totalAvailable)
                        );
                    });
        }

        inventoryToReserve.setAvailableQuantity(inventoryToReserve.getAvailableQuantity() - requestedQty);
        inventoryToReserve.setReservedQuantity(inventoryToReserve.getReservedQuantity() + requestedQty);
        inventoryToReserve.setUpdatedAt(Instant.now());

        Inventory saved = inventoryRepository.save(inventoryToReserve);

        log.info("[inventory-service] Reserved {} units of product {} at FC {}. Remaining available: {}",
                requestedQty, productId, saved.getFulfillmentCenterId(), saved.getAvailableQuantity());

        return ReservationResponse.builder()
                .reservationId("RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .productId(saved.getProductId())
                .fulfillmentCenterId(saved.getFulfillmentCenterId())
                .reservedQuantity(requestedQty)
                .remainingAvailableQuantity(saved.getAvailableQuantity())
                .reservedAt(Instant.now())
                .build();
    }

    @Override
    @Transactional
    public void releaseInventory(ReleaseRequest request) {
        Inventory inventory = inventoryRepository.findByProductIdAndFulfillmentCenterId(
                request.getProductId(), request.getFulfillmentCenterId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Inventory record not found for product [%s] at FC [%s]",
                                request.getProductId(), request.getFulfillmentCenterId())
                ));

        int newReserved = Math.max(0, inventory.getReservedQuantity() - request.getQuantity());
        inventory.setReservedQuantity(newReserved);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantity());
        inventory.setUpdatedAt(Instant.now());

        inventoryRepository.save(inventory);
        log.info("[inventory-service] Released {} units of product {} at FC {}. Restored available: {}",
                request.getQuantity(), request.getProductId(), request.getFulfillmentCenterId(), inventory.getAvailableQuantity());
    }
}
