package com.fraudetection.auth_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CPFValidator implements ConstraintValidator<CPF, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }

        String digits = value.replaceAll("[^0-9]", "");

        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }

        int firstCheckDigit = calculateCheckDigit(digits.substring(0, 9), 10);
        int secondCheckDigit = calculateCheckDigit(digits.substring(0, 9) + firstCheckDigit, 11);

        return digits.equals(digits.substring(0, 9) + firstCheckDigit + secondCheckDigit);
    }

    private int calculateCheckDigit(String base, int startWeight) {
        int sum = 0;
        int weight = startWeight;
        for (char c : base.toCharArray()) {
            sum += Character.getNumericValue(c) * weight--;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
