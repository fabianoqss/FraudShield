package com.fraudetection.fraud_detection_service.repositories;

import com.fraudetection.fraud_detection_service.entities.FraudAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface FraudAnalysisRepository extends JpaRepository<FraudAnalysis, UUID> {

    boolean existsBySourceAccountIdAndDeviceId(UUID sourceAccountId, String deviceId);

    long countBySourceAccountIdAndAnalyzedAtAfter(UUID sourceAccountId, Instant since);

    @Query("SELECT AVG(f.amount) FROM FraudAnalysis f WHERE f.sourceAccountId = :sourceAccountId AND f.analyzedAt >= :since")
    BigDecimal findAverageAmountBySourceAccountIdSince(@Param("sourceAccountId") UUID sourceAccountId, @Param("since") Instant since);
}
