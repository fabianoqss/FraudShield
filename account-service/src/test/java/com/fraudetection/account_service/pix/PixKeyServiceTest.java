package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.clients.AuthServiceClient;
import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.pix.dto.PixKeyResponse;
import com.fraudetection.account_service.pix.exceptions.InactiveAccountException;
import com.fraudetection.account_service.pix.exceptions.InvalidPixKeyFormatException;
import com.fraudetection.account_service.pix.exceptions.PixKeyAlreadyRegisteredException;
import com.fraudetection.account_service.pix.exceptions.PixKeyLimitReachedException;
import com.fraudetection.account_service.pix.exceptions.PixKeyNotFoundException;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.account_service.services.exceptions.AccountNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PixKeyServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PixKeyRepository pixKeyRepository = mock(PixKeyRepository.class);
    private final AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PixKeyService service = new PixKeyService(accountRepository, pixKeyRepository, authServiceClient,
            TransactionOperations.withoutTransaction(), meterRegistry);

    private final UUID userId = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());
        account.setOwnerId(userId);
        account.setOwnerName("Ana Souza");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(authServiceClient.lookupById(userId))
                .thenReturn(new UserLookupResponse(userId, "Ana Souza", "Ana@Example.com", "529.982.247-25"));
        when(pixKeyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void cpfKeyValueComesFromAuthServiceNormalized() {
        PixKeyResponse response = service.register(account.getId(), userId, PixKeyType.CPF);

        assertThat(response.type()).isEqualTo(PixKeyType.CPF);
        assertThat(response.value()).isEqualTo("52998224725");
        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(meterRegistry.counter("pix.key.registrations", "type", "CPF").count()).isEqualTo(1.0);
    }

    @Test
    void emailKeyValueComesFromAuthServiceNormalized() {
        assertThat(service.register(account.getId(), userId, PixKeyType.EMAIL).value()).isEqualTo("ana@example.com");
    }

    @Test
    void randomKeyIsGeneratedWithoutCallingAuthService() {
        PixKeyResponse response = service.register(account.getId(), userId, PixKeyType.RANDOM);

        assertThat(UUID.fromString(response.value()).toString()).isEqualTo(response.value());
        verifyNoInteractions(authServiceClient);
    }

    @Test
    void registrationStoresOwnerAndAccount() {
        service.register(account.getId(), userId, PixKeyType.CPF);

        ArgumentCaptor<PixKey> saved = ArgumentCaptor.forClass(PixKey.class);
        verify(pixKeyRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(userId);
        assertThat(saved.getValue().getAccount()).isSameAs(account);
    }

    @Test
    void unknownAccountIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(accountRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(unknown, userId, PixKeyType.CPF))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void anotherUsersAccountIsDenied() {
        assertThatThrownBy(() -> service.register(account.getId(), UUID.randomUUID(), PixKeyType.RANDOM))
                .isInstanceOf(AccountAccessDeniedException.class);
        verify(pixKeyRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveAccountIsRejected() {
        account.setStatus("BLOCKED");

        assertThatThrownBy(() -> service.register(account.getId(), userId, PixKeyType.RANDOM))
                .isInstanceOf(InactiveAccountException.class);
    }

    @Test
    void accountDeactivatedBeforeTheLockIsRejected() {
        Account lockedAccount = new Account();
        lockedAccount.setId(account.getId());
        lockedAccount.setOwnerId(userId);
        lockedAccount.setStatus("BLOCKED");
        when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(lockedAccount));

        assertThatThrownBy(() -> service.register(account.getId(), userId, PixKeyType.RANDOM))
                .isInstanceOf(InactiveAccountException.class);
        verify(pixKeyRepository, never()).saveAndFlush(any());
    }

    @Test
    void sixthKeyIsRejected() {
        when(pixKeyRepository.countByAccount_Id(account.getId())).thenReturn(5L);

        assertThatThrownBy(() -> service.register(account.getId(), userId, PixKeyType.RANDOM))
                .isInstanceOf(PixKeyLimitReachedException.class);
        verify(pixKeyRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateValueMapsToAlreadyRegistered() {
        when(pixKeyRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_pix_keys_key_value"));

        assertThatThrownBy(() -> service.register(account.getId(), userId, PixKeyType.CPF))
                .isInstanceOf(PixKeyAlreadyRegisteredException.class);
    }

    @Test
    void listReturnsTheAccountKeys() {
        PixKey key = key(PixKeyType.RANDOM, UUID.randomUUID().toString());
        when(pixKeyRepository.findByAccount_IdOrderByCreatedAtAsc(account.getId())).thenReturn(List.of(key));

        assertThat(service.list(account.getId(), userId)).extracting(PixKeyResponse::value).containsExactly(key.getKeyValue());
    }

    @Test
    void listOfAnotherUsersAccountIsDenied() {
        assertThatThrownBy(() -> service.list(account.getId(), UUID.randomUUID()))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void deleteRemovesTheKey() {
        PixKey key = key(PixKeyType.RANDOM, UUID.randomUUID().toString());
        when(pixKeyRepository.findByIdAndAccount_Id(key.getId(), account.getId())).thenReturn(Optional.of(key));

        service.delete(account.getId(), key.getId(), userId);

        verify(pixKeyRepository).delete(key);
    }

    @Test
    void deleteOfUnknownKeyIsNotFound() {
        UUID keyId = UUID.randomUUID();
        when(pixKeyRepository.findByIdAndAccount_Id(keyId, account.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(account.getId(), keyId, userId))
                .isInstanceOf(PixKeyNotFoundException.class);
    }

    @Test
    void resolveAccountIdNormalizesTheTypedKey() {
        PixKey key = key(PixKeyType.CPF, "52998224725");
        when(pixKeyRepository.findByKeyValue("52998224725")).thenReturn(Optional.of(key));

        assertThat(service.resolveAccountId(" 529.982.247-25 ")).isEqualTo(account.getId());
    }

    @Test
    void resolveAccountIdOfUnregisteredKeyIsNotFound() {
        when(pixKeyRepository.findByKeyValue("ana@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAccountId("ana@example.com")).isInstanceOf(PixKeyNotFoundException.class);
    }

    @Test
    void resolveAccountIdRejectsInvalidFormat() {
        assertThatThrownBy(() -> service.resolveAccountId("not a key")).isInstanceOf(InvalidPixKeyFormatException.class);
    }

    private PixKey key(PixKeyType type, String value) {
        PixKey key = new PixKey();
        key.setId(UUID.randomUUID());
        key.setKeyType(type);
        key.setKeyValue(value);
        key.setAccount(account);
        key.setOwnerId(userId);
        return key;
    }
}
