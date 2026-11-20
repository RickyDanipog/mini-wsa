package com.akamai.wsa.gateway.interfaces.rest;

public class MalformedRequestException extends RuntimeException {

    private final String field;

    public String field() {
        return field;
    }

    public MalformedRequestException(String field, String message) {
        super(message);
        this.field = field;
    }
}
