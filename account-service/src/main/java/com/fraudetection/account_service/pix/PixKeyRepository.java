package com.fraudetection.account_service.pix;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {

    long countByAccount_Id(UUID accountId);

    List<PixKey> findByAccount_IdOrderByCreatedAtAsc(UUID accountId);

    Optional<PixKey> findByIdAndAccount_Id(UUID id, UUID accountId);

    Optional<PixKey> findByKeyValue(String keyValue);
}
