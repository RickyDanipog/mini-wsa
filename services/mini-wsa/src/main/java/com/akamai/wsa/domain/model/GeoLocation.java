package com.akamai.wsa.domain.model;

/**
 * Approximate geographic origin of the request. Both fields are optional.
 */
public record GeoLocation(String country, String city) {
}
