package com.akamai.wsa.gateway.interfaces.rest.dto;

/** Optional inbound geo-location for the originating client. */
public record GeoLocationDto(String country, String city) {
}
