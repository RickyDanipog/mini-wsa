package com.akamai.wsa.gateway.interfaces.rest;

public class MalformedRequestException extends RuntimeException {

    public MalformedRequestException(String message) {
        super(message);
    }
}
