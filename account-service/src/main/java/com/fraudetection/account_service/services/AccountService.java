package com.fraudetection.account_service.services;

import com.fraudetection.account_service.clients.AuthServiceClient;
import com.fraudetection.account_service.dto.response.AccountResponse;
import com.fraudetection.account_service.dto.response.BalanceResponse;
import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.dto.request.CreateAccountRequest;
import com.fraudetection.account_service.dto.request.PixDepositRequest;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.account_service.services.exceptions.AccountNotFoundException;
import com.fraudetection.account_service.services.exceptions.PixKeyNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AuthServiceClient authServiceClient;

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
                account.getOwnerName(),
                account.getId(),
                account.getBalance().setScale(2, RoundingMode.HALF_UP),
                account.getLockedBalance().setScale(2, RoundingMode.HALF_UP),
                account.getBalance().subtract(account.getLockedBalance()).setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Transactional
    public BalanceResponse depositByPixKey(PixDepositRequest request) {
        UserLookupResponse user = request.isEmailKey()
                ? authServiceClient.lookupByEmail(request.pixKey())
                : authServiceClient.lookupByCpf(request.pixKey());

        Account account = accountRepository.findFirstByOwnerId(user.userId())
                .orElseThrow(PixKeyNotFoundException::new);

        accountRepository.creditBalance(account.getId(), request.amount());

        BigDecimal newBalance = account.getBalance().add(request.amount());

        return new BalanceResponse(
                account.getOwnerName(),
                account.getId(),
                newBalance.setScale(2, RoundingMode.HALF_UP),
                account.getLockedBalance().setScale(2, RoundingMode.HALF_UP),
                newBalance.subtract(account.getLockedBalance()).setScale(2, RoundingMode.HALF_UP),
                user.email(),
                user.cpf()
        );
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
