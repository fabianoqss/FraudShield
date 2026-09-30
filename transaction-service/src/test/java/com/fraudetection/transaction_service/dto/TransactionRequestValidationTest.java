package com.fraudetection.transaction_service.dto;

import com.fraudetection.transaction_service.dto.request.TransactionRequest;
import com.fraudetection.transaction_service.enums.PaymentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAmountsWithUpToTwoDecimals() {
        assertThat(violationsFor("150.12")).isEmpty();
        assertThat(violationsFor("150")).isEmpty();
    }

    @Test
    void rejectsAmountsWithMoreThanTwoDecimals() {
        assertThat(violationsFor("150.123")).extracting(v -> v.getPropertyPath().toString()).containsExactly("amount");
    }

    @Test
    void rejectsAmountsBeyondFifteenIntegerDigits() {
        assertThat(violationsFor("1234567890123456.00")).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    private Set<ConstraintViolation<TransactionRequest>> violationsFor(String amount) {
        return validator.validate(new TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount),
                PaymentType.PIX, "device", "key"));
    }
}
