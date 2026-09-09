package com.fraudetection.fraud_detection_service.clients;

import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.dto.response.MlPredictResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Slf4j
@Component
public class MlModelServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "mlModelService";

    private final RestClient restClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public MlModelServiceClient(@Value("${ML_MODEL_SERVICE_URL:http://localhost:8085}") String mlModelServiceUrl,
                                 CircuitBreakerFactory circuitBreakerFactory) {
        this.restClient = RestClient.create(mlModelServiceUrl);
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public MlPredictResponse predict(MlPredictRequest request) {
        return circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                .run(() -> callPredict(request), throwable -> fallback(request, throwable));
    }

    private MlPredictResponse callPredict(MlPredictRequest request) {
        return restClient.post()
                .uri("/predict")
                .body(request)
                .retrieve()
                .body(MlPredictResponse.class);
    }

    private MlPredictResponse fallback(MlPredictRequest request, Throwable throwable) {
        log.warn("ml-model-service unavailable, falling back to a neutral score for manual review. Reason: {}",
                throwable.getMessage());
        return new MlPredictResponse(new BigDecimal("0.50"), "fallback", 0);
    }
}
