package com.scalefulfill.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalefulfill.common.OrderStatus;
import com.scalefulfill.inventory.dto.InventoryResponse;
import com.scalefulfill.inventory.service.InventoryService;
import com.scalefulfill.order.dto.CreateOrderItemDto;
import com.scalefulfill.order.dto.CreateOrderRequest;
import com.scalefulfill.order.dto.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Order Lifecycle End-to-End Integration Tests")
class OrderLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryService inventoryService;

    @Test
    @DisplayName("Complete Lifecycle: Create Order -> Verify Decrement -> Cancel -> Verify Restoration")
    void testCompleteOrderLifecycleWithInventoryIntegrity() throws Exception {
        // Initial inventory for PROD-102 (Mechanical Gaming Keyboard): BLR has 80
        InventoryResponse initialInv = inventoryService.getInventory("PROD-102");
        int initialAvailable = initialInv.getTotalAvailableQuantity();
        assertTrue(initialAvailable >= 10, "Baseline inventory must be available");

        // 1. Create Order for 5 units of PROD-102
        CreateOrderRequest createRequest = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        CreateOrderItemDto.builder()
                                .productId("PROD-102")
                                .quantity(5)
                                .build()
                ))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.assignedFcId").isNotEmpty())
                .andReturn();

        OrderResponse createdOrder = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), OrderResponse.class);
        String orderId = createdOrder.getOrderId();

        // 2. Verify inventory decremented by 5
        InventoryResponse postCreateInv = inventoryService.getInventory("PROD-102");
        assertEquals(initialAvailable - 5, postCreateInv.getTotalAvailableQuantity());
        assertEquals(5, postCreateInv.getTotalReservedQuantity());

        // 3. Get Order by ID
        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.items[0].quantity").value(5));

        // 4. Cancel Order
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 5. Verify inventory restored
        InventoryResponse postCancelInv = inventoryService.getInventory("PROD-102");
        assertEquals(initialAvailable, postCancelInv.getTotalAvailableQuantity());
        assertEquals(0, postCancelInv.getTotalReservedQuantity());
    }

    @Test
    @DisplayName("Validation failure on empty items returns 400 Bad Request with structured error")
    void testCreateOrderValidationFailure() throws Exception {
        CreateOrderRequest invalidRequest = CreateOrderRequest.builder()
                .customerId("") // Blank customer ID
                .items(List.of()) // Empty items
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    @DisplayName("Order creation exceeding available stock returns 409 Conflict")
    void testCreateOrderExceedingStockReturnsConflict() throws Exception {
        CreateOrderRequest excessRequest = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        CreateOrderItemDto.builder()
                                .productId("PROD-103") // Delhi has 100
                                .quantity(9999)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_INVENTORY"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
