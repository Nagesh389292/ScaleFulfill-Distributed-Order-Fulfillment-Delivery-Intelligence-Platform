package com.scalefulfill.inventory.inbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventId implements Serializable {
    private String eventId;
    private String consumerGroup;
}
