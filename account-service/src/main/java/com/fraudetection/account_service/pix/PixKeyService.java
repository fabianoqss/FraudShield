package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.clients.AuthServiceClient;
import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.pix.dto.PixKeyResponse;
import com.fraudetection.account_service.pix.exceptions.InactiveAccountException;
import com.fraudetection.account_service.pix.exceptions.PixKeyAlreadyRegisteredException;
import com.fraudetection.account_service.pix.exceptions.PixKeyLimitReachedException;
import com.fraudetection.account_service.pix.exceptions.PixKeyNotFoundException;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.account_service.services.exceptions.AccountNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PixKeyService {

    static final int MAX_KEYS_PER_ACCOUNT = 5;
    private static final String ACTIVE = "ACTIVE";

    private final AccountRepository accountRepository;
    private final PixKeyRepository pixKeyRepository;
    private final AuthServiceClient authServiceClient;
    private final TransactionOperations transactionOperations;
    private final MeterRegistry meterRegistry;

    // Not @Transactional: the auth-service call must not hold a DB connection. Only the limit check and
    // the insert run in a transaction, with the account row locked so concurrent registrations on the
    // same account cannot exceed the limit.
    public PixKeyResponse register(UUID accountId, UUID requestingUserId, PixKeyType type) {
        Account account = loadOwnedAccount(accountId, requestingUserId);
        if (!ACTIVE.equals(account.getStatus())) {
            throw new InactiveAccountException(accountId);
        }

        String value = resolveValue(type, requestingUserId);
        PixKey saved = transactionOperations.execute(status -> insert(accountId, requestingUserId, type, value));

        meterRegistry.counter("pix.key.registrations", "type", type.name()).increment();
        log.info("Registered {} PIX key {} on account {}", type, PixKeyMasker.mask(type, value), accountId);
        return PixKeyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PixKeyResponse> list(UUID accountId, UUID requestingUserId) {
        loadOwnedAccount(accountId, requestingUserId);
        return pixKeyRepository.findByAccount_IdOrderByCreatedAtAsc(accountId).stream()
                .map(PixKeyResponse::from)
                .toList();
    }

    @Transactional
    public void delete(UUID accountId, UUID keyId, UUID requestingUserId) {
        loadOwnedAccount(accountId, requestingUserId);
        PixKey key = pixKeyRepository.findByIdAndAccount_Id(keyId, accountId)
                .orElseThrow(PixKeyNotFoundException::new);
        pixKeyRepository.delete(key);
        log.info("Removed {} PIX key {} from account {}", key.getKeyType(),
                PixKeyMasker.mask(key.getKeyType(), key.getKeyValue()), accountId);
    }

    @Transactional(readOnly = true)
    public UUID resolveAccountId(String rawKey) {
        ParsedPixKey parsed = PixKeyParser.parse(rawKey);
        return pixKeyRepository.findByKeyValue(parsed.value())
                .map(key -> key.getAccount().getId())
                .orElseThrow(PixKeyNotFoundException::new);
    }

    private PixKey insert(UUID accountId, UUID ownerId, PixKeyType type, String value) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!ACTIVE.equals(account.getStatus())) {
            throw new InactiveAccountException(accountId);
        }
        if (pixKeyRepository.countByAccount_Id(accountId) >= MAX_KEYS_PER_ACCOUNT) {
            throw new PixKeyLimitReachedException();
        }

        PixKey key = new PixKey();
        key.setKeyType(type);
        key.setKeyValue(value);
        key.setAccount(account);
        key.setOwnerId(ownerId);
        try {
            return pixKeyRepository.saveAndFlush(key);
        } catch (DataIntegrityViolationException e) {
            throw new PixKeyAlreadyRegisteredException();
        }
    }

    private String resolveValue(PixKeyType type, UUID userId) {
        return switch (type) {
            case RANDOM -> UUID.randomUUID().toString();
            case CPF -> PixKeyParser.normalizeCpf(owner(userId).cpf());
            case EMAIL -> PixKeyParser.normalizeEmail(owner(userId).email());
        };
    }

    private UserLookupResponse owner(UUID userId) {
        return authServiceClient.lookupById(userId);
    }

    private Account loadOwnedAccount(UUID accountId, UUID requestingUserId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!account.getOwnerId().equals(requestingUserId)) {
            throw new AccountAccessDeniedException("Account does not belong to the requesting user");
        }
        return account;
    }
}
