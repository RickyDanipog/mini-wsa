package com.akamai.wsa.eventstore.domain.model;

/** Geo of the client IP, as owned by event-store. */
public record StoredGeoLocation(String country, String city) {
}
