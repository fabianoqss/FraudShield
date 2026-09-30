package com.fraudetection.fraud_detection_service.services;

import com.fraudetection.fraud_detection_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.repositories.FraudAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureEngineerServiceTest {

    private FeatureEngineerService featureEngineerService;

    @BeforeEach
    void setUp() {
        FraudAnalysisRepository fraudAnalysisRepository = Mockito.mock(FraudAnalysisRepository.class);
        featureEngineerService = new FeatureEngineerService(fraudAnalysisRepository, ZoneId.of("America/Sao_Paulo"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null, false",
            "abc, false",
            "10.1.2.3, false",
            "192.168.0.10, false",
            "127.0.0.1, false",
            "172.16.0.1, false",
            "172.31.255.255, false",
            "172.15.0.1, true",
            "172.32.0.1, true",
            "172.217.0.1, true",
            "8.8.8.8, true",
            "::1, false",
            "fec0::1, false",
            "fe80::1, false",
            "fc00::1, false",
            "fdff:ffff::1, false",
            "2001:4860:4860::8888, true",
            "fbff::1, true",
            "fe00::1, true",
            "not:a:valid:ip, false"
    }, nullValues = "null")
    void isForeignIp(String ipAddress, boolean expected) {
        assertEquals(expected, featureEngineerService.isForeignIp(ipAddress));
    }

    @Test
    void timeFeaturesUseTheBusinessZoneNotUtc() {
        // 00:30 UTC on Wednesday is 21:30 on Tuesday in São Paulo, where the customer made the transfer.
        TransactionCreatedPayload payload = JsonMapper.builder().build().readValue("""
                {"transactionId":"56c0f553-c6c7-4fc7-9bbc-82c7a608ba4c",
                 "sourceAccountId":"8c7cd4d6-57ca-41e1-8b3e-71c57dfab297",
                 "destinationAccountId":"e10f6f6b-0269-43a9-bc33-2ff54789ccff",
                 "amount":150.00,"type":"PIX","deviceId":"device","ipAddress":"203.0.113.5",
                 "createdAt":"2026-09-30T00:30:00Z"}
                """, TransactionCreatedPayload.class);

        MlPredictRequest features = featureEngineerService.buildFeatures(payload);

        assertEquals(21, features.hourOfDay());
        assertEquals(2, features.dayOfWeek());
    }
}
