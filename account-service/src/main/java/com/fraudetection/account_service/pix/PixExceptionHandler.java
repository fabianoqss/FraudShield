package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.dto.response.ErrorResponse;
import com.fraudetection.account_service.pix.exceptions.InactiveAccountException;
import com.fraudetection.account_service.pix.exceptions.InvalidPixKeyFormatException;
import com.fraudetection.account_service.pix.exceptions.PixKeyAlreadyRegisteredException;
import com.fraudetection.account_service.pix.exceptions.PixKeyLimitReachedException;
import com.fraudetection.account_service.pix.exceptions.PixKeyNotFoundException;
import com.fraudetection.account_service.pix.exceptions.PixLookupNotFoundException;
import com.fraudetection.account_service.pix.exceptions.PixLookupRateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ordered first: GlobalExceptionHandler has a catch-all Exception handler that would otherwise win.
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PixExceptionHandler {

    @ExceptionHandler(PixKeyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PixKeyNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidPixKeyFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormat(InvalidPixKeyFormatException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(PixKeyAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyRegistered(PixKeyAlreadyRegisteredException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({PixKeyLimitReachedException.class, InactiveAccountException.class})
    public ResponseEntity<ErrorResponse> handleBusinessRule(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), request);
    }

    @ExceptionHandler(PixLookupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLookupNotFound(PixLookupNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(PixLookupRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(PixLookupRateLimitedException ex, HttpServletRequest request) {
        ResponseEntity<ErrorResponse> response = build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(response.getBody());
    }

    // Fail closed: without Redis there is no rate limit and no lookup storage.
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisDown(RedisConnectionFailureException ex, HttpServletRequest request) {
        log.error("Redis unavailable on {}", request.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "PIX key lookup is temporarily unavailable", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        log.warn("PIX request {} failed with {}: {}", request.getRequestURI(), status.value(), message);
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
