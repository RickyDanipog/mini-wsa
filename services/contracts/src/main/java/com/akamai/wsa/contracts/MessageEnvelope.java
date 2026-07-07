package com.akamai.wsa.contracts;

import java.time.Instant;

public record MessageEnvelope<T>(
        String correlationId,
        Instant occurredAt,
        int version,
        T payload
) {
    public static final int CURRENT_VERSION = 1;

    public static <T> MessageEnvelope<T> of(String correlationId, Instant occurredAt, T payload) {
        return new MessageEnvelope<>(correlationId, occurredAt, CURRENT_VERSION, payload);
    }
}
