package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.dto.RegisterRequest;
import com.fraudetection.auth_service.dto.UserResponse;

import com.fraudetection.auth_service.repositories.UserRepository;
import com.fraudetection.auth_service.services.exceptions.EmailAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public UserResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyExistsException(registerRequest.email());
        }


        throw new UnsupportedOperationException("not implemented yet");
    }

}
