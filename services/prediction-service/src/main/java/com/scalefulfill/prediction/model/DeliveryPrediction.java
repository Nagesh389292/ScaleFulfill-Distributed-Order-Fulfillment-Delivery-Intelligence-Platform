package com.scalefulfill.prediction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPrediction {
    private String predictionId;
    private String orderId;
    private String customerId;
    private String assignedFcId;
    private String modelVersion; // e.g. "eta-v1" (analytical estimator baseline)
    private Double predictedEtaMinutes;
    private Double demandVelocityScore;
    private Instant generatedAt;
    private Instant expiresAt;
    private boolean stale;
    private Map<String, Object> featureVector;

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
