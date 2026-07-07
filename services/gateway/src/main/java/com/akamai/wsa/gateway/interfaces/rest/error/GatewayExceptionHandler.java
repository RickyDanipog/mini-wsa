package com.akamai.wsa.gateway.interfaces.rest.error;

import com.akamai.wsa.gateway.interfaces.rest.MalformedRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Maps gateway request failures to the structured {@code {error:{code,message,details[]}}} 400 body. */
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

    @ExceptionHandler({MalformedRequestException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorBody> onMalformed(Exception malformedException) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorBody(
                new ApiErrorBody.ApiError("MALFORMED_REQUEST", "request body could not be parsed", List.of())));
    }
}
