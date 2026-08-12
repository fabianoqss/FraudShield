package com.fraudetection.transaction_service.services;

import com.fraudetection.transaction_service.dto.response.TransactionResponse;
import com.fraudetection.transaction_service.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;


    public TransactionResponse createRequest(){



        return null;
    }

    public TransactionResponse getRequest(){



        return null;
    }
}
