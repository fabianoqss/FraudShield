package com.fraudetection.auth_service.dto.request;

import com.fraudetection.auth_service.validation.CPF;
import com.fraudetection.auth_service.validation.MinAge;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record RegisterRequest(
        @Schema(example = "Ana Souza")
        @NotBlank(message = "Full name is required")
        String fullName,

        @Schema(description = "Stored trimmed and lower-cased, so login and duplicate checks ignore case.",
                example = "ana.souza@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid format")
        String email,

        @Schema(description = "11 digits with valid check digits; dots and dash are accepted and stripped.",
                example = "52998224725")
        @NotBlank(message = "CPF is required")
        @CPF(message = "CPF must be a valid format")
        String cpf,

        @Schema(description = "At least 8 characters with an uppercase letter, a lowercase letter, a number and a "
                + "special character.", example = "Senha@123")
        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~]).{8,}$",
                message = "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number and a special character"
        )
        String password,

        @Schema(description = "The user must be at least 18 years old.", example = "1990-05-20")
        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        @MinAge(value = 18, message = "Must be at least 18 years old")
        LocalDate birthDate
) {
}
