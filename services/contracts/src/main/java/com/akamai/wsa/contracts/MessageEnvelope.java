package com.akamai.wsa.contracts;

import java.time.Instant;

/**
 * The envelope wrapping every Kafka message: a correlationId for tracing across
 * the async pipeline, the time the event occurred, a schema version, and the
 * typed payload.
 */
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
