package com.fraudetection.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~]).{8,}$",
                message = "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number and a special character"
        )
        String newPassword,
        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~]).{8,}$",
                message = "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number and a special character"
        )
        String confirmPassword
) {
}
