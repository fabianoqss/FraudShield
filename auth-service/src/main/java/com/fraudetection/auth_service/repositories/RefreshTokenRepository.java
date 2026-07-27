package com.fraudetection.auth_service.repositories;

import com.fraudetection.auth_service.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :revokedAt where r.id = :id and r.revokedAt is null")
    int revokeById(@Param("id") UUID id, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :revokedAt where r.userId = :userId and r.revokedAt is null")
    void revokeAllActiveForUser(@Param("userId") UUID userId, @Param("revokedAt") LocalDateTime revokedAt);
}
