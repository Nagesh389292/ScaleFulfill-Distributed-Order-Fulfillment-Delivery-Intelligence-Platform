package com.scalefulfill.prediction.calculator;

import com.scalefulfill.prediction.model.OrderPredictionEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Computes dynamic product demand velocity score in range [0.0, 1.0].
 * High velocity scores signal fast-moving SKUs requiring expedited cross-docking or localized pre-allocation.
 */
@Component
public class DemandVelocityCalculator {

    public double calculateVelocityScore(OrderPredictionEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            return 0.50;
        }

        int totalUnits = event.getItems().stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 1)
                .sum();

        BigDecimal amount = event.getTotalAmount() != null ? event.getTotalAmount() : BigDecimal.ZERO;

        // Velocity normalization formula: 0.5 * (units / 20) + 0.5 * (amount / 1000)
        double unitFactor = Math.min(1.0, totalUnits / 20.0);
        double valueFactor = Math.min(1.0, amount.doubleValue() / 1000.0);

        double score = 0.5 * unitFactor + 0.5 * valueFactor;
        return Math.round(score * 100.0) / 100.0;
    }
}
