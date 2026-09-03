package com.fraudetection.fraud_detection_service.entities;

import com.fraudetection.fraud_detection_service.enums.Decision;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tb_fraud_analyses")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FraudAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal fraudScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features_snapshot", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> featuresSnapshot;

    @Column(nullable = false, length = 50)
    private String modelVersion;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant analyzedAt;

    @Column(nullable = false)
    private UUID sourceAccountId;

    private String deviceId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
}
