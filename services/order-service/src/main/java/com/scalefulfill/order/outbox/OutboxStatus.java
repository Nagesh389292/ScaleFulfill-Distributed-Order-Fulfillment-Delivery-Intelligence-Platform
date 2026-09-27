package com.scalefulfill.order.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
