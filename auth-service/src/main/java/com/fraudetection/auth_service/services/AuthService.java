package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.dto.RegisterRequest;
import com.fraudetection.auth_service.dto.UserResponse;

import com.fraudetection.auth_service.entities.User;
import com.fraudetection.auth_service.repositories.UserRepository;
import com.fraudetection.auth_service.services.exceptions.EmailAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public void register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyExistsException(registerRequest.email());
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(registerRequest.fullName());
        user.setEmail(registerRequest.email());
        user.setCpf(registerRequest.cpf());
        user.setPasswordHash(registerRequest.password());

        userRepository.save(user);
    }


}
