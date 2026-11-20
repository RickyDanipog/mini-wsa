package com.akamai.wsa.gateway.interfaces.rest.error;

import com.akamai.wsa.gateway.interfaces.rest.MalformedRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(BatchValidationException.class)
    public ResponseEntity<ApiErrorBody> onBatchValidation(BatchValidationException batchValidationException) {
        List<ApiErrorBody.ApiError.FieldViolation> details = batchValidationException.violations().stream()
                .map(itemViolation -> new ApiErrorBody.ApiError.FieldViolation(
                        itemViolation.field(), itemViolation.message()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorBody(
                new ApiErrorBody.ApiError("VALIDATION_FAILED", "one or more events are invalid", details)));
    }

    @ExceptionHandler(MalformedRequestException.class)
    public ResponseEntity<ApiErrorBody> onInvalidField(MalformedRequestException exception) {
        return malformed(new ApiErrorBody.ApiError.FieldViolation(exception.field(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> onMalformed() {
        return malformed(new ApiErrorBody.ApiError.FieldViolation("body", "Expected a JSON request body"));
    }

    private ResponseEntity<ApiErrorBody> malformed(ApiErrorBody.ApiError.FieldViolation detail) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorBody(
                new ApiErrorBody.ApiError("MALFORMED_REQUEST", "request body could not be parsed", List.of(detail))));
    }
}
