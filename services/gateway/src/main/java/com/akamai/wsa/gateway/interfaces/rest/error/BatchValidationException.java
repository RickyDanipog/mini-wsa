package com.akamai.wsa.gateway.interfaces.rest.error;

import java.util.List;

public class BatchValidationException extends RuntimeException {

    public record ItemViolation(String field, String message) {
    }

    private final transient List<ItemViolation> violations;

    public BatchValidationException(List<ItemViolation> violations) {
        super("batch validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<ItemViolation> violations() {
        return violations;
    }
}
