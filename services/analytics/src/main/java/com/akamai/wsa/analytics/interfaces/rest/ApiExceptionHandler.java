package com.akamai.wsa.analytics.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Maps query-parameter binding problems on the read endpoints to structured 400 responses. */
@RestControllerAdvice(assignableTypes = {StatsController.class, SamplesController.class})
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String detail = "Invalid value '%s' for parameter '%s'".formatted(exception.getValue(), exception.getName());
        return badRequest(detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        return badRequest(exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> badRequest(String detail) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "timestamp", Instant.now().toString(),
                "details", List.of(detail)));
    }
}
