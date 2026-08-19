package com.fraudetection.transaction_service.entities;

import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_transactions")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    private UUID sourceAccountId;

    private UUID destinationAccountId;

    private BigDecimal amount;

    private PaymentType type;

    private PaymentStatus status;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private LocalDateTime createdAt;

    private String deviceId;

    private String ipAddress;

}
