package com.fraudetection.fraud_detection_service.services;

import com.fraudetection.fraud_detection_service.dto.event.TransactionCreatedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudAnalysisService {

    private final FeatureEngineerService  featureEngineerService;

    public void analyze(TransactionCreatedPayload payload){



    }
}
