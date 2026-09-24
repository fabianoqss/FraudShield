package com.fraudetection.account_service.controllers;

import com.fraudetection.account_service.dto.response.AccountResponse;
import com.fraudetection.account_service.dto.response.BalanceResponse;
import com.fraudetection.account_service.dto.response.DepositResponse;
import com.fraudetection.account_service.dto.request.CreateAccountRequest;
import com.fraudetection.account_service.dto.request.PixDepositRequest;
import com.fraudetection.account_service.services.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> list(Authentication authentication) {
        UUID requestingUserId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(accountService.listAccounts(requestingUserId));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request,
                                                    Authentication authentication) {
        UUID requestingUserId = UUID.fromString(authentication.getName());
        AccountResponse response = accountService.createAccount(request, requestingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id, Authentication authentication) {
        UUID requestingUserId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(accountService.getBalance(id, requestingUserId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<DepositResponse> deposit(@Valid @RequestBody PixDepositRequest request) {
        return ResponseEntity.ok(accountService.depositByPixKey(request));
    }
}
