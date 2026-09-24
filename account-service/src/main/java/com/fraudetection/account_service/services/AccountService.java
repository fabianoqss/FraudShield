package com.fraudetection.account_service.services;

import com.fraudetection.account_service.dto.response.AccountResponse;
import com.fraudetection.account_service.dto.response.BalanceResponse;
import com.fraudetection.account_service.dto.response.DepositResponse;
import com.fraudetection.account_service.dto.request.CreateAccountRequest;
import com.fraudetection.account_service.dto.request.PixDepositRequest;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.pix.PixKeyService;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.account_service.services.exceptions.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PixKeyService pixKeyService;

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

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(UUID requestingUserId) {
        return accountRepository.findByOwnerIdOrderByCreatedAtAsc(requestingUserId).stream()
                .map(this::toAccountResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId, UUID requestingUserId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getOwnerId().equals(requestingUserId)) {
            throw new AccountAccessDeniedException("Account does not belong to the requesting user");
        }

        return new BalanceResponse(
                account.getOwnerName(),
                account.getId(),
                account.getBalance().setScale(2, RoundingMode.HALF_UP),
                account.getLockedBalance().setScale(2, RoundingMode.HALF_UP),
                account.getBalance().subtract(account.getLockedBalance()).setScale(2, RoundingMode.HALF_UP)
        );
    }

    // Not @Transactional: the key lookup and the credit are independent single statements;
    // creditBalance is one atomic UPDATE in its own transaction.
    public DepositResponse depositByPixKey(PixDepositRequest request) {
        UUID accountId = pixKeyService.resolveAccountId(request.pixKey());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        accountRepository.creditBalance(account.getId(), request.amount());

        return new DepositResponse(account.getOwnerName(), request.amount().setScale(2, RoundingMode.HALF_UP));
    }

    private AccountResponse toAccountResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerId(),
                account.getOwnerName(),
                account.getBalance().setScale(2, RoundingMode.HALF_UP),
                account.getLockedBalance().setScale(2, RoundingMode.HALF_UP),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
