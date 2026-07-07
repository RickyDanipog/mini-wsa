package com.akamai.wsa.gateway.interfaces.rest.error;

import java.util.List;

/** Structured error body: {@code {error:{code,message,details:[{field,message}]}}}. */
public record ApiErrorBody(ApiError error) {

    public record ApiError(String code, String message, List<FieldViolation> details) {

        public record FieldViolation(String field, String message) {
        }
    }
}
