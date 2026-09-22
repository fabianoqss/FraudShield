package com.fraudetection.fraud_detection_service.services;

import com.fraudetection.fraud_detection_service.repositories.FraudAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureEngineerServiceTest {

    private FeatureEngineerService featureEngineerService;

    @BeforeEach
    void setUp() {
        FraudAnalysisRepository fraudAnalysisRepository = Mockito.mock(FraudAnalysisRepository.class);
        featureEngineerService = new FeatureEngineerService(fraudAnalysisRepository);
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
            "8.8.8.8, true"
    }, nullValues = "null")
    void isForeignIp(String ipAddress, boolean expected) {
        assertEquals(expected, featureEngineerService.isForeignIp(ipAddress));
    }
}
