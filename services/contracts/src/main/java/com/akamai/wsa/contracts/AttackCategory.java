package com.akamai.wsa.contracts;

/** Machine category on a security rule (shared across services). */
public enum AttackCategory {
    INJECTION,
    XSS,
    PROTOCOL_VIOLATION,
    DATA_LEAKAGE,
    BOT,
    DOS,
    RATE_LIMIT
}
