package com.fraudetection.fraud_detection_service.clients;

import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.dto.response.MlPredictResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Slf4j
@Component
public class MlModelServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "mlModelService";

    private final RestClient restClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final Counter contractErrors;

    public MlModelServiceClient(@Value("${ML_MODEL_SERVICE_URL:http://localhost:8085}") String mlModelServiceUrl,
                                 CircuitBreakerFactory circuitBreakerFactory,
                                 MeterRegistry meterRegistry) {
        this.restClient = RestClient.create(mlModelServiceUrl);
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.contractErrors = Counter.builder("ml.predict.contract.errors")
                .description("Predict requests rejected by ml-model-service with a 4xx: a Java/Python contract bug")
                .register(meterRegistry);
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

    MlPredictResponse fallback(MlPredictRequest request, Throwable throwable) {
        if (throwable instanceof HttpClientErrorException rejected) {
            // The model is up but refused the request: retrying cannot help and the fallback would hide it.
            contractErrors.increment();
            log.error("ml-model-service rejected the predict request ({}), falling back to a neutral score for "
                    + "manual review. This is a contract bug. Body: {}", rejected.getStatusCode(),
                    rejected.getResponseBodyAsString());
        } else {
            log.warn("ml-model-service unavailable, falling back to a neutral score for manual review. Reason: {}",
                    throwable.getMessage());
        }
        return new MlPredictResponse(new BigDecimal("0.50"), "fallback", 0);
    }
}
