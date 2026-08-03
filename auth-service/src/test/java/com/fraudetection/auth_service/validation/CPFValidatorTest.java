package com.fraudetection.auth_service.validation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CPFValidatorTest {

    private final CPFValidator validator = new CPFValidator();

    @Test
    void isValidShouldReturnTrueWhenCpfIsValid(){
        String validCPF = "707.148.934-39";
        String anotherPaternCPF = "70714893439";

        boolean result = validator.isValid(validCPF, null);
        boolean resultAnotherPatern = validator.isValid(anotherPaternCPF, null);

        Assertions.assertTrue(result);
        Assertions.assertTrue(resultAnotherPatern);
    }



}
