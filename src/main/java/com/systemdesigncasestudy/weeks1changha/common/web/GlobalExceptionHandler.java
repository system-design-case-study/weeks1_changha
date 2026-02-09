package com.systemdesigncasestudy.weeks1changha.common.web;

import com.systemdesigncasestudy.weeks1changha.common.exception.NotFoundException;
import com.systemdesigncasestudy.weeks1changha.common.response.ApiError;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        ConstraintViolationException.class,
        MethodArgumentNotValidException.class,
        BindException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleInternal(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected error");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        String requestId = UUID.randomUUID().toString();
        String safeMessage = message == null || message.isBlank() ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(new ApiError(code, safeMessage, requestId));
    }
}
