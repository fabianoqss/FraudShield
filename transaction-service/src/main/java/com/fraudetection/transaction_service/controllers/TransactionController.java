package com.fraudetection.transaction_service.controllers;

import com.fraudetection.transaction_service.dto.response.TransactionResponse;
import com.fraudetection.transaction_service.services.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createRequest(){

        return null;
    }

    @GetMapping
    public ResponseEntity<TransactionResponse> getRequest(){

        return null;
    }

}

