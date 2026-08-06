package com.hanjisang.pis.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class P15ExceptionHandler {

    @ExceptionHandler(P15BusinessException.class)
    public ResponseEntity<Map<String, Object>> handle(P15BusinessException exception) {
        return ResponseEntity.status(exception.httpStatus()).body(Map.of(
                "operation_identity", UUID.randomUUID(),
                "correlation_identity", UUID.randomUUID(),
                "error_code", exception.errorCode(),
                "message", exception.getMessage(),
                "processing_state_code", "REJECTED",
                "safe_retry_code", "REVIEW_REQUIRED",
                "occurred_at", Instant.now()));
    }
}
