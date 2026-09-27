package com.scalefulfill.prediction.repository;

import com.scalefulfill.prediction.model.CustomerLocation;
import com.scalefulfill.prediction.model.FulfillmentCenter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local read model registry holding Fulfillment Center parameters and Customer Geolocation indices.
 * Decouples PredictionService from synchronous calls to upstream services.
 */
@Component
public class FulfillmentCenterRegistry {

    private final Map<String, FulfillmentCenter> fulfillmentCenters = new ConcurrentHashMap<>();
    private final Map<String, CustomerLocation> customerLocations = new ConcurrentHashMap<>();

    public FulfillmentCenterRegistry() {
        seedInitialReadModel();
    }

    private void seedInitialReadModel() {
        // Seed 5 Regional Fulfillment Centers across coordinate space (e.g. US metro hubs)
        // FC-NORTH (Chicago, IL: 41.8781, -87.6298)
        Map<String, Integer> invNorth = new HashMap<>();
        invNorth.put("PROD-1001", 500);
        invNorth.put("PROD-1002", 300);
        invNorth.put("PROD-1003", 200);
        fulfillmentCenters.put("FC-NORTH", FulfillmentCenter.builder()
                .id("FC-NORTH")
                .name("North Regional FC - Chicago")
                .latitude(41.8781)
                .longitude(-87.6298)
                .dispatchCapacityPerHour(120.0)
                .handlingTimeMinutes(15.0)
                .baseCost(4.50)
                .costPerKm(0.08)
                .activeQueueDepth(new AtomicInteger(8))
                .inventory(invNorth)
                .build());

        // FC-SOUTH (Dallas, TX: 32.7767, -96.7970)
        Map<String, Integer> invSouth = new HashMap<>();
        invSouth.put("PROD-1001", 400);
        invSouth.put("PROD-1002", 450);
        invSouth.put("PROD-1003", 100);
        fulfillmentCenters.put("FC-SOUTH", FulfillmentCenter.builder()
                .id("FC-SOUTH")
                .name("South Regional FC - Dallas")
                .latitude(32.7767)
                .longitude(-96.7970)
                .dispatchCapacityPerHour(100.0)
                .handlingTimeMinutes(18.0)
                .baseCost(4.20)
                .costPerKm(0.075)
                .activeQueueDepth(new AtomicInteger(12))
                .inventory(invSouth)
                .build());

        // FC-EAST (Newark, NJ: 40.7357, -74.1724)
        Map<String, Integer> invEast = new HashMap<>();
        invEast.put("PROD-1001", 600);
        invEast.put("PROD-1002", 200);
        invEast.put("PROD-1003", 400);
        fulfillmentCenters.put("FC-EAST", FulfillmentCenter.builder()
                .id("FC-EAST")
                .name("East Regional FC - Newark")
                .latitude(40.7357)
                .longitude(-74.1724)
                .dispatchCapacityPerHour(150.0)
                .handlingTimeMinutes(12.0)
                .baseCost(5.00)
                .costPerKm(0.09)
                .activeQueueDepth(new AtomicInteger(15))
                .inventory(invEast)
                .build());

        // FC-WEST (Seattle, WA: 47.6062, -122.3321)
        Map<String, Integer> invWest = new HashMap<>();
        invWest.put("PROD-1001", 350);
        invWest.put("PROD-1002", 500);
        invWest.put("PROD-1003", 300);
        fulfillmentCenters.put("FC-WEST", FulfillmentCenter.builder()
                .id("FC-WEST")
                .name("West Regional FC - Seattle")
                .latitude(47.6062)
                .longitude(-122.3321)
                .dispatchCapacityPerHour(110.0)
                .handlingTimeMinutes(14.0)
                .baseCost(4.80)
                .costPerKm(0.085)
                .activeQueueDepth(new AtomicInteger(6))
                .inventory(invWest)
                .build());

        // FC-CENTRAL (Denver, CO: 39.7392, -104.9903)
        Map<String, Integer> invCentral = new HashMap<>();
        invCentral.put("PROD-1001", 300);
        invCentral.put("PROD-1002", 250);
        invCentral.put("PROD-1003", 250);
        fulfillmentCenters.put("FC-CENTRAL", FulfillmentCenter.builder()
                .id("FC-CENTRAL")
                .name("Central Regional FC - Denver")
                .latitude(39.7392)
                .longitude(-104.9903)
                .dispatchCapacityPerHour(90.0)
                .handlingTimeMinutes(20.0)
                .baseCost(4.00)
                .costPerKm(0.07)
                .activeQueueDepth(new AtomicInteger(5))
                .inventory(invCentral)
                .build());

        // Seed customer geolocation index (seeded ScaleFulfill customers)
        customerLocations.put("CUST-1001", CustomerLocation.builder()
                .customerId("CUST-1001")
                .latitude(40.7128)
                .longitude(-74.0060)
                .city("New York")
                .build());

        customerLocations.put("CUST-1002", CustomerLocation.builder()
                .customerId("CUST-1002")
                .latitude(34.0522)
                .longitude(-118.2437)
                .city("Los Angeles")
                .build());

        customerLocations.put("CUST-1003", CustomerLocation.builder()
                .customerId("CUST-1003")
                .latitude(41.8781)
                .longitude(-87.6298)
                .city("Chicago")
                .build());
    }

    public FulfillmentCenter getFulfillmentCenter(String fcId) {
        return fulfillmentCenters.get(fcId);
    }

    public Collection<FulfillmentCenter> getAllFulfillmentCenters() {
        return Collections.unmodifiableCollection(fulfillmentCenters.values());
    }

    public CustomerLocation getCustomerLocation(String customerId) {
        return customerLocations.getOrDefault(customerId, CustomerLocation.builder()
                .customerId(customerId)
                .latitude(39.8283) // Geographic center of US (Lebanon, KS) default
                .longitude(-98.5795)
                .city("Default Regional Zone")
                .build());
    }

    public void updateQueueDepth(String fcId, int delta) {
        FulfillmentCenter fc = fulfillmentCenters.get(fcId);
        if (fc != null) {
            fc.getActiveQueueDepth().addAndGet(delta);
        }
    }
}
