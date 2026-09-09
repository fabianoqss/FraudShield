package com.fraudetection.fraud_detection_service.services;

import com.fraudetection.fraud_detection_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.repositories.FraudAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class FeatureEngineerService {

    private final FraudAnalysisRepository fraudAnalysisRepository;

    public MlPredictRequest buildFeatures(TransactionCreatedPayload payload) {
        Instant now = Instant.now();

        boolean isNewDevice = !fraudAnalysisRepository.existsBySourceAccountIdAndDeviceId(
                payload.sourceAccountId(), payload.deviceId());

        boolean isForeignIp = isForeignIp(payload.ipAddress());

        long transactionsLastHour = fraudAnalysisRepository.countBySourceAccountIdAndAnalyzedAtAfter(
                payload.sourceAccountId(), now.minus(1, ChronoUnit.HOURS));

        long transactionsLast24Hours = fraudAnalysisRepository.countBySourceAccountIdAndAnalyzedAtAfter(
                payload.sourceAccountId(), now.minus(24, ChronoUnit.HOURS));

        BigDecimal avgAmountLast30Days = fraudAnalysisRepository.findAverageAmountBySourceAccountIdSince(
                payload.sourceAccountId(), now.minus(30, ChronoUnit.DAYS));

        return new MlPredictRequest(
                payload.amount(),
                payload.createdAt().getHour(),
                payload.createdAt().getDayOfWeek().getValue(),
                payload.type(),
                isNewDevice,
                isForeignIp,
                transactionsLastHour,
                transactionsLast24Hours,
                avgAmountLast30Days != null ? avgAmountLast30Days : BigDecimal.ZERO
        );
    }

    private boolean isForeignIp(String ipAddress) {
        if (ipAddress == null) {
            return false;
        }

        return !(ipAddress.startsWith("10.")
                || ipAddress.startsWith("192.168.")
                || ipAddress.startsWith("127.")
                || ipAddress.startsWith("172."));
    }

}
