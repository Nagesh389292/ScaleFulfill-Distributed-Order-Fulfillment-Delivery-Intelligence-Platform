package com.scalefulfill.prediction.calculator;

import com.scalefulfill.prediction.model.FulfillmentCenter;
import org.springframework.stereotype.Component;

/**
 * Deterministic analytical prediction model / baseline estimator (eta-v1).
 * Formulates delivery ETA from kinematic transit velocity, physical distance,
 * handling overhead, and fulfillment queue backpressure.
 */
@Component
public class EtaPredictor {

    public static final String MODEL_VERSION = "eta-v1";

    /**
     * Compute analytical predicted ETA in minutes:
     * ETA = (Distance / Velocity) * 60 + HandlingTime + (QueueDepth / DispatchRate) * 60
     */
    public double predictEtaMinutes(double distanceKm, double velocityKmh, FulfillmentCenter fc) {
        double transitTimeMinutes = (distanceKm / Math.max(velocityKmh, 10.0)) * 60.0;
        double handlingTimeMinutes = fc.getHandlingTimeMinutes();
        double queueTimeMinutes = (fc.getActiveQueueDepth().get() / Math.max(fc.getDispatchCapacityPerHour(), 1.0)) * 60.0;

        double totalEtaMinutes = transitTimeMinutes + handlingTimeMinutes + queueTimeMinutes;
        return Math.round(totalEtaMinutes * 10.0) / 10.0;
    }
}
