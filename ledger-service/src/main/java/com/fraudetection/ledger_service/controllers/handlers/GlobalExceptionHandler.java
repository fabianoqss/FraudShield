package com.fraudetection.ledger_service.controllers.handlers;

import com.fraudetection.ledger_service.dto.response.ErrorResponse;
import com.fraudetection.ledger_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.ledger_service.services.exceptions.AccountNotFoundException;
import com.fraudetection.ledger_service.services.exceptions.AccountServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        log.warn("Account lookup failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccountAccessDenied(AccountAccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountServiceUnavailable(AccountServiceUnavailableException ex, HttpServletRequest request) {
        log.error("Ledger query failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
