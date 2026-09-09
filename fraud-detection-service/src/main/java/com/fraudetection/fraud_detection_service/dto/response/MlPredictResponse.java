package com.fraudetection.fraud_detection_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlPredictResponse(
        BigDecimal fraudScore,
        String modelVersion,
        long inferenceTimeMs
) {
}
