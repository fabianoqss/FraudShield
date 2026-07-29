package com.fraudetection.auth_service.dto.request;

import com.fraudetection.auth_service.validation.CPF;
import com.fraudetection.auth_service.validation.MinAge;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid format")
        String email,

        @NotBlank(message = "CPF is required")
        @CPF(message = "CPF must be a valid format")
        String cpf,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~]).{8,}$",
                message = "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number and a special character"
        )
        String password,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        @MinAge(value = 18, message = "Must be at least 18 years old")
        LocalDate birthDate
) {
}
