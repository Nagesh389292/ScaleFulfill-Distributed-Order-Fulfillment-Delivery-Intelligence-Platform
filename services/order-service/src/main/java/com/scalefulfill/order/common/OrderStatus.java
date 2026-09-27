package com.scalefulfill.order.common;

public enum OrderStatus {
    PENDING,
    INVENTORY_CHECK,
    RESERVED,
    PENDING_INVENTORY_VERIFICATION, // Resilience fallback state when downstream is degraded
    REJECTED,
    FULFILLMENT_ASSIGNED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
