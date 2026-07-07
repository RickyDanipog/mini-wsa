package com.akamai.wsa.contracts;

/** Action the security engine took on the request (shared across services). */
public enum Action {
    DENY,
    ALERT,
    MONITOR
}
