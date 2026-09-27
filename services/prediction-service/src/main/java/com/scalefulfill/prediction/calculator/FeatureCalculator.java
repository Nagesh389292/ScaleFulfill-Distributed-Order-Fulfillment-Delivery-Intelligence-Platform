package com.scalefulfill.prediction.calculator;

import com.scalefulfill.prediction.model.CustomerLocation;
import com.scalefulfill.prediction.model.FulfillmentCenter;
import com.scalefulfill.prediction.model.OrderPredictionEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Feature engineering calculator for physical distance, carrier transit velocity, package weight, and FC queue delay.
 */
@Component
public class FeatureCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double DEFAULT_UNIT_WEIGHT_KG = 0.5;

    /**
     * Compute Haversine great-circle distance between customer and FC in kilometers.
     */
    public double calculateDistanceKm(CustomerLocation customer, FulfillmentCenter fc) {
        double lat1Rad = Math.toRadians(customer.getLatitude());
        double lon1Rad = Math.toRadians(customer.getLongitude());
        double lat2Rad = Math.toRadians(fc.getLatitude());
        double lon2Rad = Math.toRadians(fc.getLongitude());

        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculate effective carrier transit velocity based on distance classification.
     * Urban (< 50 km) -> 35 km/h; Regional (50 - 300 km) -> 55 km/h; Interstate (> 300 km) -> 75 km/h.
     */
    public double estimateCarrierVelocityKmh(double distanceKm) {
        if (distanceKm < 50.0) {
            return 35.0;
        } else if (distanceKm < 300.0) {
            return 55.0;
        } else {
            return 75.0;
        }
    }

    /**
     * Aggregate total consignment weight in kilograms.
     */
    public double calculateTotalWeightKg(OrderPredictionEvent event) {
        if (event.getItems() == null || event.getItems().isEmpty()) {
            return 1.0;
        }
        return event.getItems().stream()
                .mapToDouble(item -> {
                    double unitWeight = item.getWeightKg() != null ? item.getWeightKg() : DEFAULT_UNIT_WEIGHT_KG;
                    int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                    return unitWeight * qty;
                })
                .sum();
    }

    /**
     * Extract features into structured vector.
     */
    public Map<String, Object> extractFeatures(CustomerLocation customer, FulfillmentCenter fc,
                                               OrderPredictionEvent event, double distanceKm,
                                               double velocityKmh, double weightKg) {
        Map<String, Object> features = new HashMap<>();
        features.put("distanceKm", Math.round(distanceKm * 100.0) / 100.0);
        features.put("carrierVelocityKmh", velocityKmh);
        features.put("totalWeightKg", Math.round(weightKg * 100.0) / 100.0);
        features.put("fcQueueDepth", fc.getActiveQueueDepth().get());
        features.put("dispatchCapacityPerHour", fc.getDispatchCapacityPerHour());
        features.put("handlingTimeMinutes", fc.getHandlingTimeMinutes());
        return features;
    }
}
