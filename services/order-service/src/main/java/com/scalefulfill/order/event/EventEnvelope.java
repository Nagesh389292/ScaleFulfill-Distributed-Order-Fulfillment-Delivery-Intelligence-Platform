package com.scalefulfill.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private Instant occurredAt;
    private String correlationId;
    private T payload;
}
