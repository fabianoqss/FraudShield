package com.fraudetection.fraud_detection_service.dto.request;

import com.fraudetection.fraud_detection_service.enums.PaymentType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record MlPredictRequest(
        BigDecimal amount,
        int hourOfDay,
        int dayOfWeek,
        PaymentType transactionType,
        boolean isNewDevice,
        boolean isForeignIp,
        long transactionsLastHour,
        long transactionsLast24Hours,
        BigDecimal avgAmountLast30Days
) {

    // ml-model-service accepts at most 4 decimals; a PostgreSQL AVG returns many more (66.6666666666666667).
    private static final int MODEL_SCALE = 4;

    public MlPredictRequest {
        amount = toModelScale(amount);
        avgAmountLast30Days = avgAmountLast30Days == null ? BigDecimal.ZERO : toModelScale(avgAmountLast30Days);
    }

    private static BigDecimal toModelScale(BigDecimal value) {
        return value == null || value.scale() <= MODEL_SCALE ? value : value.setScale(MODEL_SCALE, RoundingMode.HALF_EVEN);
    }
}
