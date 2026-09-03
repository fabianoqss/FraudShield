package com.fraudetection.fraud_detection_service.repositories;

import com.fraudetection.fraud_detection_service.entities.FraudAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudAnalysisRepository extends JpaRepository<FraudAnalysis, UUID> {
}
