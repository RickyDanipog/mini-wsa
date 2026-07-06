package com.akamai.wsa.domain.model;

/**
 * Value object for a client IP address. Equality is by value.
 */
public record ClientIp(String value) {

    public ClientIp {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("clientIp must not be blank");
        }
    }
}
