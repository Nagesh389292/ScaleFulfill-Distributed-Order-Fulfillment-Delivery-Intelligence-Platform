package com.scalefulfill.inventory.service;

import com.scalefulfill.common.exception.InsufficientInventoryException;
import com.scalefulfill.common.exception.ResourceNotFoundException;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.dto.ReservationResponse;
import com.scalefulfill.inventory.entity.Inventory;
import com.scalefulfill.inventory.entity.Product;
import com.scalefulfill.inventory.repository.FulfillmentCenterRepository;
import com.scalefulfill.inventory.repository.InventoryRepository;
import com.scalefulfill.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inventory Service Unit Tests")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private FulfillmentCenterRepository fulfillmentCenterRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id("PROD-100")
                .sku("SKU-LAPTOP")
                .name("Laptop")
                .price(BigDecimal.valueOf(50000))
                .weightKg(BigDecimal.valueOf(1.5))
                .build();

        inventory = Inventory.builder()
                .id(1L)
                .productId("PROD-100")
                .fulfillmentCenterId("FC-BLR-01")
                .availableQuantity(10)
                .reservedQuantity(0)
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("Reserve inventory successfully when stock is available")
    void testReserveInventorySuccess() {
        when(productRepository.existsById("PROD-100")).thenReturn(true);
        when(inventoryRepository.findByProductIdAndFulfillmentCenterId("PROD-100", "FC-BLR-01"))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationRequest request = ReservationRequest.builder()
                .productId("PROD-100")
                .fulfillmentCenterId("FC-BLR-01")
                .quantity(4)
                .build();

        ReservationResponse response = inventoryService.reserveInventory(request);

        assertNotNull(response);
        assertEquals(4, response.getReservedQuantity());
        assertEquals(6, response.getRemainingAvailableQuantity());
        assertEquals("FC-BLR-01", response.getFulfillmentCenterId());
        verify(inventoryRepository, times(1)).save(inventory);
    }

    @Test
    @DisplayName("Throw InsufficientInventoryException when requested quantity exceeds available")
    void testReserveInventoryInsufficientStock() {
        when(productRepository.existsById("PROD-100")).thenReturn(true);
        when(inventoryRepository.findByProductIdAndFulfillmentCenterId("PROD-100", "FC-BLR-01"))
                .thenReturn(Optional.of(inventory));

        ReservationRequest request = ReservationRequest.builder()
                .productId("PROD-100")
                .fulfillmentCenterId("FC-BLR-01")
                .quantity(15) // available is 10
                .build();

        assertThrows(InsufficientInventoryException.class, () -> inventoryService.reserveInventory(request));
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Release inventory restores available quantity")
    void testReleaseInventoryRestoresStock() {
        inventory.setAvailableQuantity(6);
        inventory.setReservedQuantity(4);

        when(inventoryRepository.findByProductIdAndFulfillmentCenterId("PROD-100", "FC-BLR-01"))
                .thenReturn(Optional.of(inventory));

        inventoryService.releaseInventory("PROD-100", "FC-BLR-01", 4);

        assertEquals(10, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        verify(inventoryRepository, times(1)).save(inventory);
    }
}
