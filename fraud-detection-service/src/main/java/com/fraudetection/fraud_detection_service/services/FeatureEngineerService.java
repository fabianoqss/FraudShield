package com.fraudetection.fraud_detection_service.services;

import com.fraudetection.fraud_detection_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.fraud_detection_service.repositories.FraudAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeatureEngineerService {

    private final FraudAnalysisRepository featureEngineerRepository;

    public void buildFeatures(TransactionCreatedPayload payload){
        
    }

}
