package com.fraudetection.fraud_detection_service.services;

import tools.jackson.databind.ObjectMapper;
import com.fraudetection.fraud_detection_service.clients.MlModelServiceClient;
import com.fraudetection.fraud_detection_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.dto.response.MlPredictResponse;
import com.fraudetection.fraud_detection_service.entities.FraudAnalysis;
import com.fraudetection.fraud_detection_service.enums.Decision;
import com.fraudetection.fraud_detection_service.kafka.producers.TransactionApprovedProducer;
import com.fraudetection.fraud_detection_service.kafka.producers.TransactionDeniedProducer;
import com.fraudetection.fraud_detection_service.kafka.producers.TransactionFlaggedProducer;
import com.fraudetection.fraud_detection_service.repositories.FraudAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAnalysisService {

    private static final BigDecimal FLAGGED_THRESHOLD = new BigDecimal("0.30");
    private static final BigDecimal DENIED_THRESHOLD = new BigDecimal("0.70");
    private static final String HIGH_FRAUD_SCORE_REASON = "HIGH_FRAUD_SCORE";
    private static final String MEDIUM_FRAUD_SCORE_REASON = "MEDIUM_FRAUD_SCORE";

    private final FeatureEngineerService featureEngineerService;
    private final MlModelServiceClient mlModelServiceClient;
    private final FraudAnalysisRepository fraudAnalysisRepository;
    private final TransactionApprovedProducer transactionApprovedProducer;
    private final TransactionDeniedProducer transactionDeniedProducer;
    private final TransactionFlaggedProducer transactionFlaggedProducer;
    private final ObjectMapper objectMapper;

    public void analyze(TransactionCreatedPayload payload) {
        MlPredictRequest features = featureEngineerService.buildFeatures(payload);
        MlPredictResponse mlResponse = mlModelServiceClient.predict(features);

        Decision decision = decide(mlResponse.fraudScore());

        FraudAnalysis fraudAnalysis = new FraudAnalysis();
        fraudAnalysis.setTransactionId(payload.transactionId());
        fraudAnalysis.setFraudScore(mlResponse.fraudScore());
        fraudAnalysis.setDecision(decision);
        fraudAnalysis.setReason(reasonFor(decision));
        fraudAnalysis.setFeaturesSnapshot(toSnapshot(features));
        fraudAnalysis.setModelVersion(mlResponse.modelVersion());
        fraudAnalysis.setSourceAccountId(payload.sourceAccountId());
        fraudAnalysis.setDestinationAccountId(payload.destinationAccountId());
        fraudAnalysis.setDeviceId(payload.deviceId());
        fraudAnalysis.setAmount(payload.amount());

        FraudAnalysis saved = fraudAnalysisRepository.save(fraudAnalysis);

        log.info("Transaction {} analyzed: score={}, decision={}",
                payload.transactionId(), mlResponse.fraudScore(), decision);

        publish(saved, decision);
    }

    private Decision decide(BigDecimal fraudScore) {
        if (fraudScore.compareTo(DENIED_THRESHOLD) >= 0) {
            return Decision.DENIED;
        }
        if (fraudScore.compareTo(FLAGGED_THRESHOLD) >= 0) {
            return Decision.FLAGGED;
        }
        return Decision.APPROVED;
    }

    private String reasonFor(Decision decision) {
        return switch (decision) {
            case DENIED -> HIGH_FRAUD_SCORE_REASON;
            case FLAGGED -> MEDIUM_FRAUD_SCORE_REASON;
            case APPROVED -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toSnapshot(MlPredictRequest features) {
        return objectMapper.convertValue(features, Map.class);
    }

    private void publish(FraudAnalysis fraudAnalysis, Decision decision) {
        switch (decision) {
            case APPROVED -> transactionApprovedProducer.publish(fraudAnalysis);
            case DENIED -> transactionDeniedProducer.publish(fraudAnalysis);
            case FLAGGED -> transactionFlaggedProducer.publish(fraudAnalysis);
        }
    }
}
