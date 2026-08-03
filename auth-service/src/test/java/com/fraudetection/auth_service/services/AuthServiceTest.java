package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AuthServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void exceptionShouldReturnExceptionWhenTheCPFAlreadyExists(){



    }


}
