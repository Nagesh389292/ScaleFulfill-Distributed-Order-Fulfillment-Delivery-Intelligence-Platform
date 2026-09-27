package com.scalefulfill.prediction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentCenter {
    private String id;
    private String name;
    private double latitude;
    private double longitude;
    private double dispatchCapacityPerHour;
    private double handlingTimeMinutes;
    private double baseCost;
    private double costPerKm;
    @Builder.Default
    private AtomicInteger activeQueueDepth = new AtomicInteger(0);
    private Map<String, Integer> inventory; // productId -> available quantity
}
