package com.fraudetection.fraud_detection_service.dto;

import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.enums.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MlPredictRequestTest {

    @Test
    void roundsMonetaryFeaturesToTheFourDecimalsTheModelAccepts() {
        MlPredictRequest request = request(new BigDecimal("150.12"), new BigDecimal("66.6666666666666667"));

        assertThat(request.avgAmountLast30Days()).isEqualByComparingTo("66.6667");
        assertThat(request.avgAmountLast30Days().scale()).isEqualTo(4);
        assertThat(request.amount()).isEqualByComparingTo("150.12");
        assertThat(request.amount().scale()).isLessThanOrEqualTo(4);
    }

    @Test
    void treatsAMissingAverageAsZero() {
        assertThat(request(BigDecimal.TEN, null).avgAmountLast30Days()).isEqualByComparingTo("0");
    }

    private static MlPredictRequest request(BigDecimal amount, BigDecimal average) {
        return new MlPredictRequest(amount, 12, 3, PaymentType.PIX, false, false, 0, 0, average);
    }
}
