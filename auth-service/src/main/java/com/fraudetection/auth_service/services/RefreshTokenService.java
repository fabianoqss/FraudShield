package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.entities.RefreshToken;
import com.fraudetection.auth_service.repositories.RefreshTokenRepository;
import com.fraudetection.auth_service.services.exceptions.InvalidRefreshTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${refresh-token.expiration-ms}") long expirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(expirationMs)));

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for user {}; revoking all active sessions", existing.getUserId());
            refreshTokenRepository.revokeAllActiveForUser(existing.getUserId(), LocalDateTime.now());
            throw new InvalidRefreshTokenException();
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException();
        }

        int rowsRevoked = refreshTokenRepository.revokeById(existing.getId(), LocalDateTime.now());
        if (rowsRevoked == 0) {
            log.warn("Concurrent refresh token reuse detected for user {}; revoking all active sessions", existing.getUserId());
            refreshTokenRepository.revokeAllActiveForUser(existing.getUserId(), LocalDateTime.now());
            throw new InvalidRefreshTokenException();
        }

        return new RefreshResult(existing.getUserId(), issue(existing.getUserId()));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(existing -> refreshTokenRepository.revokeById(existing.getId(), LocalDateTime.now()));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, LocalDateTime.now());
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public record RefreshResult(UUID userId, String rawToken) {
    }
}
