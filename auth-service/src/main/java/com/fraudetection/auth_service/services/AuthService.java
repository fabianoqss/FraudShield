package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.dto.request.ChangePasswordRequest;
import com.fraudetection.auth_service.dto.request.LoginRequest;
import com.fraudetection.auth_service.dto.request.RegisterRequest;
import com.fraudetection.auth_service.dto.response.AuthResponse;
import com.fraudetection.auth_service.dto.response.UserLookupResponse;
import com.fraudetection.auth_service.dto.response.UserResponse;
import com.fraudetection.auth_service.entities.User;
import com.fraudetection.auth_service.repositories.UserRepository;
import com.fraudetection.auth_service.security.JwtService;
import com.fraudetection.auth_service.services.exceptions.DuplicateCpfException;
import com.fraudetection.auth_service.services.exceptions.EmailAlreadyExistsException;
import com.fraudetection.auth_service.services.exceptions.InvalidCredentialsException;
import com.fraudetection.auth_service.services.exceptions.InvalidCurrentPasswordException;
import com.fraudetection.auth_service.services.exceptions.InvalidRefreshTokenException;
import com.fraudetection.auth_service.services.exceptions.PasswordMismatchException;
import com.fraudetection.auth_service.services.exceptions.UserNotFoundException;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    public UserResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyExistsException(registerRequest.email());
        }

        if (userRepository.existsByCpf(registerRequest.cpf())) {
            throw new DuplicateCpfException(registerRequest.cpf());
        }

        User user = new User();
        user.setFullName(registerRequest.fullName());
        user.setEmail(registerRequest.email());
        user.setCpf(registerRequest.cpf());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        user.setBirthDate(registerRequest.birthDate());

        User saved = userRepository.save(user);

        return new UserResponse(saved.getId(), saved.getEmail(), saved.getFullName(), saved.getBirthDate(), saved.getCreatedAt());
    }

    public AuthResponse login(LoginRequest loginRequest) {
        User user;

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
            );
            user = (User) authentication.getPrincipal();
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());

        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.getExpirationSeconds());
    }

    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.RefreshResult result = refreshTokenService.rotate(refreshToken);

        User user = userRepository.findById(result.userId())
                .orElseThrow(InvalidRefreshTokenException::new);

        String accessToken = jwtService.generateToken(user);

        return new AuthResponse(accessToken, result.rawToken(), "Bearer", jwtService.getExpirationSeconds());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public UserLookupResponse lookup(String email, String cpf) {
        User user;

        if (StringUtils.hasText(email)) {
            user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        } else if (StringUtils.hasText(cpf)) {
            user = userRepository.findByCpf(cpf).orElseThrow(UserNotFoundException::new);
        } else {
            throw new UserNotFoundException();
        }

        return new UserLookupResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCpf());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new PasswordMismatchException();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(userId);
    }


}
