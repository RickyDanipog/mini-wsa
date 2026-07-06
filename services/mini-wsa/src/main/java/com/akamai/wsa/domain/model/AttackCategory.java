package com.akamai.wsa.domain.model;

/**
 * The machine-readable category emitted on a security rule (rule.category).
 * Closed vocabulary defined by the assignment.
 */
public enum AttackCategory {
    INJECTION,
    XSS,
    PROTOCOL_VIOLATION,
    DATA_LEAKAGE,
    BOT,
    DOS,
    RATE_LIMIT
}
