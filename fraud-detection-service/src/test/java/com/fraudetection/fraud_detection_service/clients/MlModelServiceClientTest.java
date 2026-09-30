package com.fraudetection.fraud_detection_service.clients;

import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.dto.response.MlPredictResponse;
import com.fraudetection.fraud_detection_service.enums.PaymentType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MlModelServiceClientTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MlModelServiceClient client =
            new MlModelServiceClient("http://localhost:1", mock(CircuitBreakerFactory.class), meterRegistry);
    private final MlPredictRequest request =
            new MlPredictRequest(BigDecimal.TEN, 12, 3, PaymentType.PIX, false, false, 0, 0, BigDecimal.ONE);

    @Test
    void countsARejectedRequestAsAContractError() {
        MlPredictResponse response = client.fallback(request,
                HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable", null, null, null));

        assertThat(response.modelVersion()).isEqualTo("fallback");
        assertThat(meterRegistry.counter("ml.predict.contract.errors").count()).isEqualTo(1.0);
    }

    @Test
    void doesNotCountUnavailabilityAsAContractError() {
        client.fallback(request, new ResourceAccessException("Connection refused"));

        assertThat(meterRegistry.counter("ml.predict.contract.errors").count()).isZero();
    }
}
