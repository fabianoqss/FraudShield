package com.fraudetection.fraud_detection_service.dto.request;

import com.fraudetection.fraud_detection_service.enums.PaymentType;

import java.math.BigDecimal;

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
}
