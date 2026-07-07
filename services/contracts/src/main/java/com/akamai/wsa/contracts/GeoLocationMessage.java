package com.akamai.wsa.contracts;

/** Wire form of the request's approximate geographic origin. */
public record GeoLocationMessage(String country, String city) {
}
