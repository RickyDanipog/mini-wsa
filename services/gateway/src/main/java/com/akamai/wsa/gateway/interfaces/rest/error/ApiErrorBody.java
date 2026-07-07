package com.akamai.wsa.gateway.interfaces.rest.error;

import java.util.List;

public record ApiErrorBody(ApiError error) {

    public record ApiError(String code, String message, List<FieldViolation> details) {

        public record FieldViolation(String field, String message) {
        }
    }
}
