package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.dto.AuthResponse;
import com.fraudetection.auth_service.dto.LoginRequest;
import com.fraudetection.auth_service.dto.RegisterRequest;
import com.fraudetection.auth_service.dto.UserResponse;

import com.fraudetection.auth_service.entities.User;
import com.fraudetection.auth_service.repositories.UserRepository;
import com.fraudetection.auth_service.security.JwtService;
import com.fraudetection.auth_service.services.exceptions.DuplicateCpfException;
import com.fraudetection.auth_service.services.exceptions.EmailAlreadyExistsException;
import com.fraudetection.auth_service.services.exceptions.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

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

        User saved = userRepository.save(user);

        return new UserResponse(saved.getId(), saved.getEmail(), saved.getFullName(), saved.getCreatedAt());
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

        return new AuthResponse(accessToken, "Bearer", jwtService.getExpirationSeconds());
    }
}
