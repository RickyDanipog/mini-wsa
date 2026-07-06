package com.akamai.wsa.domain.model;

import java.time.Instant;

// Aggregate root seed. Grows in later plans: attackType, threatScore,
// receivedAt, and the full DLR fields (rule, geoLocation, method, ...).
public record SecurityEvent(String eventId, int configId, Instant timestamp) {
}
