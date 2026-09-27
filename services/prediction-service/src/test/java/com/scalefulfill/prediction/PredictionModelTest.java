package com.scalefulfill.prediction;

import com.scalefulfill.prediction.calculator.DemandVelocityCalculator;
import com.scalefulfill.prediction.calculator.EtaPredictor;
import com.scalefulfill.prediction.calculator.FeatureCalculator;
import com.scalefulfill.prediction.model.CustomerLocation;
import com.scalefulfill.prediction.model.DeliveryPrediction;
import com.scalefulfill.prediction.model.FulfillmentCenter;
import com.scalefulfill.prediction.model.OrderPredictionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PredictionModelTest {

    private final FeatureCalculator featureCalculator = new FeatureCalculator();
    private final EtaPredictor etaPredictor = new EtaPredictor();
    private final DemandVelocityCalculator demandVelocityCalculator = new DemandVelocityCalculator();

    @Test
    @DisplayName("Should correctly calculate Haversine distance between NY and Chicago (~1148 km)")
    void testHaversineDistance() {
        CustomerLocation ny = CustomerLocation.builder()
                .customerId("CUST-1001")
                .latitude(40.7128)
                .longitude(-74.0060)
                .city("New York")
                .build();

        FulfillmentCenter chicago = FulfillmentCenter.builder()
                .id("FC-NORTH")
                .latitude(41.8781)
                .longitude(-87.6298)
                .build();

        double dist = featureCalculator.calculateDistanceKm(ny, chicago);
        assertTrue(dist > 1100 && dist < 1200, "Distance should be approximately 1148 km, got: " + dist);
    }

    @Test
    @DisplayName("Should evaluate deterministic analytical ETA (eta-v1)")
    void testEtaPredictionFormula() {
        FulfillmentCenter fc = FulfillmentCenter.builder()
                .id("FC-TEST")
                .dispatchCapacityPerHour(60.0)
                .handlingTimeMinutes(15.0)
                .activeQueueDepth(new AtomicInteger(10))
                .build();

        // 100 km at 50 km/h -> 2 hours (120 mins) transit
        // Handling: 15 mins
        // Queue: 10 orders / (60 orders/hr) -> 10 mins
        // Total = 120 + 15 + 10 = 145 mins
        double eta = etaPredictor.predictEtaMinutes(100.0, 50.0, fc);
        assertEquals(145.0, eta, 0.1);
    }

    @Test
    @DisplayName("Should compute demand velocity score bounded between 0 and 1")
    void testDemandVelocityScore() {
        OrderPredictionEvent event = OrderPredictionEvent.builder()
                .orderId("ORD-999")
                .totalAmount(new BigDecimal("250.00"))
                .items(List.of(
                        OrderPredictionEvent.PredictionItem.builder()
                                .productId("PROD-1001")
                                .quantity(4)
                                .build()
                ))
                .build();

        double score = demandVelocityCalculator.calculateVelocityScore(event);
        assertTrue(score >= 0.0 && score <= 1.0);
        // unitFactor = 4/20 = 0.2; valueFactor = 250/1000 = 0.25; score = 0.5*0.2 + 0.5*0.25 = 0.225 -> 0.23
        assertEquals(0.23, score, 0.02);
    }

    @Test
    @DisplayName("Should detect stale prediction when current time exceeds expiration")
    void testStalePredictionEvaluation() {
        Instant past = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant expiredAt = Instant.now().minus(10, ChronoUnit.MINUTES);

        DeliveryPrediction prediction = DeliveryPrediction.builder()
                .predictionId("PRED-STALE-1")
                .orderId("ORD-STALE-1")
                .generatedAt(past)
                .expiresAt(expiredAt)
                .stale(false)
                .build();

        assertTrue(prediction.isExpired(Instant.now()));
    }
}
