package com.fraudetection.account_service.dto;

import com.fraudetection.account_service.dto.request.PixDepositRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PixDepositRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAmountsUpToTheDepositLimitWithTwoDecimals() {
        assertThat(violationsFor("0.01")).isEmpty();
        assertThat(violationsFor("10000.00")).isEmpty();
    }

    @Test
    void rejectsAmountsWithMoreThanTwoDecimals() {
        assertThat(violationsFor("150.123")).extracting(ConstraintViolation::getMessage)
                .containsExactly("Amount must have at most 2 decimal places");
    }

    @Test
    void rejectsAmountsAboveTheDepositLimit() {
        assertThat(violationsFor("10000.01")).extracting(ConstraintViolation::getMessage)
                .containsExactly("Amount must be at most 10000.00 per deposit");
    }

    private Set<ConstraintViolation<PixDepositRequest>> violationsFor(String amount) {
        return validator.validate(new PixDepositRequest("ana.souza@example.com", new BigDecimal(amount)));
    }
}
