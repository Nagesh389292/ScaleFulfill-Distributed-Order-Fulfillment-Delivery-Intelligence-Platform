package com.scalefulfill.common;

public enum OrderStatus {
    PENDING,
    INVENTORY_CHECK,
    RESERVED,
    REJECTED,
    FULFILLMENT_ASSIGNED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
