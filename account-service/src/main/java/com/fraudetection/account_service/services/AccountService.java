package com.fraudetection.account_service.services;

import com.fraudetection.account_service.dto.AccountResponse;
import com.fraudetection.account_service.dto.BalanceResponse;
import com.fraudetection.account_service.dto.CreateAccountRequest;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.account_service.services.exceptions.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountResponse createAccount(CreateAccountRequest request, UUID requestingUserId) {
        if (!request.ownerId().equals(requestingUserId)) {
            throw new AccountAccessDeniedException("Cannot create an account for another user");
        }

        Account account = new Account();
        account.setOwnerId(request.ownerId());
        account.setOwnerName(request.ownerName());

        Account saved = accountRepository.save(account);

        return toAccountResponse(saved);
    }

    public BalanceResponse getBalance(UUID accountId, UUID requestingUserId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getOwnerId().equals(requestingUserId)) {
            throw new AccountAccessDeniedException("Account does not belong to the requesting user");
        }

        return new BalanceResponse(
                account.getId(),
                account.getBalance(),
                account.getLockedBalance(),
                account.getBalance().subtract(account.getLockedBalance())
        );
    }

    private AccountResponse toAccountResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerId(),
                account.getOwnerName(),
                account.getBalance(),
                account.getLockedBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
