package com.scalefulfill.prediction.optimization;

import com.scalefulfill.prediction.optimization.model.AllocationModels.AllocationFc;
import com.scalefulfill.prediction.optimization.model.AllocationModels.AllocationOrder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class OptimizationCostUtil {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static double calculateShippingCost(AllocationOrder order, AllocationFc fc) {
        double distanceKm = haversineDistanceKm(order.getLatitude(), order.getLongitude(),
                fc.getLatitude(), fc.getLongitude());
        return fc.getBaseCost() + (distanceKm * fc.getCostPerKm());
    }

    public static double calculateStdDev(Collection<Integer> values) {
        if (values.isEmpty()) return 0.0;
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double sumSq = 0.0;
        for (int v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / values.size());
    }
}
