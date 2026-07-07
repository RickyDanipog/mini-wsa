package com.akamai.wsa.contracts;

/** Wire form of the security rule that matched a request. */
public record RuleMessage(
        String id,
        String name,
        String message,
        Severity severity,
        AttackCategory category
) {
}
