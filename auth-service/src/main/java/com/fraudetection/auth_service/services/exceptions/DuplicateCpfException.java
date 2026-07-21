package com.fraudetection.auth_service.services.exceptions;

public class DuplicateCpfException extends RuntimeException {

    public DuplicateCpfException(String cpf) {
        super("CPF already registered: " + cpf);
    }
}
