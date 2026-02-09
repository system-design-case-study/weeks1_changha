package com.systemdesigncasestudy.weeks1changha.common.web;

import com.systemdesigncasestudy.weeks1changha.common.exception.NotFoundException;
import com.systemdesigncasestudy.weeks1changha.common.response.ApiError;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleInternal(Exception ex) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unexpected error [requestId={}]: {}", requestId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "unexpected error", requestId));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        String requestId = UUID.randomUUID().toString();
        String safeMessage = message == null || message.isBlank() ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(new ApiError(code, safeMessage, requestId));
    }
}
