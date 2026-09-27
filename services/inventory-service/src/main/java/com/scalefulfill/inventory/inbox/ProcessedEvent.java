package com.scalefulfill.inventory.inbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Id
    @Column(name = "consumer_group", length = 64, nullable = false)
    private String consumerGroup;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
