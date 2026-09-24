package com.fraudetection.account_service.services;

import com.fraudetection.account_service.dto.request.PixDepositRequest;
import com.fraudetection.account_service.dto.response.DepositResponse;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.pix.PixKeyService;
import com.fraudetection.account_service.pix.exceptions.PixKeyNotFoundException;
import com.fraudetection.account_service.repositories.AccountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceDepositTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PixKeyService pixKeyService = mock(PixKeyService.class);
    private final AccountService accountService = new AccountService(accountRepository, pixKeyService);

    @Test
    void depositCreditsTheAccountOfTheRegisteredKey() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setOwnerName("Ana Souza");
        when(pixKeyService.resolveAccountId("529.982.247-25")).thenReturn(account.getId());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        DepositResponse response = accountService.depositByPixKey(new PixDepositRequest("529.982.247-25", new BigDecimal("10")));

        assertThat(response).isEqualTo(new DepositResponse("Ana Souza", new BigDecimal("10.00")));
        verify(accountRepository).creditBalance(account.getId(), new BigDecimal("10"));
    }

    @Test
    void depositToUnregisteredKeyCreditsNothing() {
        when(pixKeyService.resolveAccountId("bia@example.com")).thenThrow(new PixKeyNotFoundException());

        assertThatThrownBy(() -> accountService.depositByPixKey(new PixDepositRequest("bia@example.com", BigDecimal.TEN)))
                .isInstanceOf(PixKeyNotFoundException.class);
        verify(accountRepository, never()).creditBalance(any(), any());
    }
}
