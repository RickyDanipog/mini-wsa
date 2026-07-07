package com.akamai.wsa.gateway.interfaces.rest;

/** Raised when the request body cannot be parsed into ingest events (bad JSON, bad enum, bad timestamp). */
public class MalformedRequestException extends RuntimeException {

    public MalformedRequestException(String message) {
        super(message);
    }
}
