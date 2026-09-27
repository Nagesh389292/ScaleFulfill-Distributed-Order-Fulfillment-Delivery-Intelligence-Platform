package com.scalefulfill.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private String productId;
    private String productName;
    private int totalAvailableQuantity;
    private int totalReservedQuantity;
    private List<FcInventoryDetail> centerDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FcInventoryDetail {
        private String fulfillmentCenterId;
        private String fulfillmentCenterCode;
        private String fulfillmentCenterName;
        private int availableQuantity;
        private int reservedQuantity;
    }
}
