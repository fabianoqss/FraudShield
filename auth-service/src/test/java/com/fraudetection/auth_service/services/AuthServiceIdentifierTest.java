package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.dto.request.LoginRequest;
import com.fraudetection.auth_service.dto.request.RegisterRequest;
import com.fraudetection.auth_service.entities.User;
import com.fraudetection.auth_service.repositories.UserRepository;
import com.fraudetection.auth_service.security.JwtService;
import com.fraudetection.auth_service.services.exceptions.DuplicateCpfException;
import com.fraudetection.auth_service.services.exceptions.EmailAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceIdentifierTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService,
            authenticationManager, refreshTokenService, loginAttemptService);

    @Test
    void registerStoresCanonicalEmailAndCpf() {
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(registerRequest(" Ana.Souza@Example.com ", "529.982.247-25"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana.souza@example.com");
        assertThat(saved.getValue().getCpf()).isEqualTo("52998224725");
    }

    @Test
    void registerRejectsEmailThatDiffersOnlyByCase() {
        when(userRepository.existsByEmail("ana.souza@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("ANA.SOUZA@example.com", "52998224725")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsCpfThatDiffersOnlyByPunctuation() {
        when(userRepository.existsByCpf("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("ana@example.com", "529.982.247-25")))
                .isInstanceOf(DuplicateCpfException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginUsesCanonicalEmail() {
        User user = new User();
        user.setId(UUID.randomUUID());
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        authService.login(new LoginRequest(" Ana@Example.com", "Senha@123"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> token =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(token.capture());
        assertThat(token.getValue().getName()).isEqualTo("ana@example.com");
        verify(loginAttemptService).recordSuccess("ana@example.com");
    }

    @Test
    void lookupFindsUserByFormattedCpfOrMixedCaseEmail() {
        User user = new User();
        user.setId(UUID.randomUUID());
        when(userRepository.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

        assertThat(authService.lookup(null, null, "529.982.247-25").userId()).isEqualTo(user.getId());
        assertThat(authService.lookup(null, "Ana@Example.com", null).userId()).isEqualTo(user.getId());
    }

    @Test
    void lookupFindsUserById() {
        User user = new User();
        user.setId(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(authService.lookup(user.getId(), null, null).userId()).isEqualTo(user.getId());
    }

    private RegisterRequest registerRequest(String email, String cpf) {
        return new RegisterRequest("Ana Souza", email, cpf, "Senha@123", LocalDate.of(1990, 1, 1));
    }
}
