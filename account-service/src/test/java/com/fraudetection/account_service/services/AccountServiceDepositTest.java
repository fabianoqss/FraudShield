package com.fraudetection.account_service.services;

import com.fraudetection.account_service.clients.AuthServiceClient;
import com.fraudetection.account_service.dto.request.PixDepositRequest;
import com.fraudetection.account_service.dto.response.DepositResponse;
import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.repositories.AccountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceDepositTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
    private final AccountService accountService = new AccountService(accountRepository, authServiceClient);

    @Test
    void depositCreditsAccountAndReturnsOnlyReceiverNameAndAmount() {
        UUID userId = UUID.randomUUID();
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setOwnerId(userId);
        account.setOwnerName("Ana Souza");
        when(authServiceClient.lookupByCpf("52998224725"))
                .thenReturn(new UserLookupResponse(userId, "Ana Souza", "ana@example.com", "52998224725"));
        when(accountRepository.findFirstByOwnerId(userId)).thenReturn(Optional.of(account));

        DepositResponse response = accountService.depositByPixKey(new PixDepositRequest("52998224725", new BigDecimal("10")));

        assertThat(response).isEqualTo(new DepositResponse("Ana Souza", new BigDecimal("10.00")));
        verify(accountRepository).creditBalance(account.getId(), new BigDecimal("10"));
    }
}
