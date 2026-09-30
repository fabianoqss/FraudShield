package com.fraudetection.auth_service.controllers;

import com.fraudetection.auth_service.dto.request.ChangePasswordRequest;
import com.fraudetection.auth_service.dto.request.LoginRequest;
import com.fraudetection.auth_service.dto.request.RefreshTokenRequest;
import com.fraudetection.auth_service.dto.request.RegisterRequest;
import com.fraudetection.auth_service.dto.response.AuthResponse;
import com.fraudetection.auth_service.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * OpenAPI documentation of the user-facing routes of {@link AuthController}. Documentation only: routing,
 * validation and behavior stay in the controller. Internal routes are excluded from the spec in application.yml.
 */
@Tag(name = "Auth", description = "Registration, login, token refresh, logout and password changes.")
public interface AuthApi {

    @SecurityRequirements
    @Operation(summary = "Register a user",
            description = "E-mail is stored trimmed and lower-cased; CPF is stored as 11 digits.")
    @ApiResponse(responseCode = "201", description = "User registered")
    @ApiResponse(responseCode = "400", description = "Validation failed; see `fieldErrors`")
    @ApiResponse(responseCode = "409", description = "E-mail or CPF already registered")
    ResponseEntity<UserResponse> register(RegisterRequest registerRequest);

    @SecurityRequirements
    @Operation(summary = "Log in",
            description = "Returns a JWT access token (`expiresIn` seconds) and an opaque refresh token. The access "
                    + "token's payload carries `sub` (user id), `email`, `fullName` and `exp`.")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "400", description = "Validation failed; see `fieldErrors`")
    @ApiResponse(responseCode = "401", description = "Wrong e-mail or password")
    @ApiResponse(responseCode = "429", description = "Too many failed logins for this e-mail; locked for 15 minutes")
    ResponseEntity<AuthResponse> login(LoginRequest loginRequest);

    @SecurityRequirements
    @Operation(summary = "Refresh the tokens",
            description = "Returns a new access token and a **new** refresh token; the old refresh token stops "
                    + "working. Reusing an old refresh token revokes all of the user's sessions, so a client must "
                    + "keep only the newest one and never retry with the previous one.")
    @ApiResponse(responseCode = "200", description = "Tokens refreshed")
    @ApiResponse(responseCode = "400", description = "Validation failed; see `fieldErrors`")
    @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired or already used; log in again")
    ResponseEntity<AuthResponse> refresh(RefreshTokenRequest request);

    @SecurityRequirements
    @Operation(summary = "Log out", description = "Revokes the refresh token. The client discards both tokens.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ApiResponse(responseCode = "400", description = "Validation failed; see `fieldErrors`")
    ResponseEntity<Void> logout(RefreshTokenRequest request);

    @Operation(summary = "Change the caller's password",
            description = "Revokes all of the user's refresh tokens: the client should log in again.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the new passwords do not match")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token, or wrong current password")
    ResponseEntity<Void> changePassword(ChangePasswordRequest request,
                                        @Parameter(hidden = true) Authentication authentication);
}
