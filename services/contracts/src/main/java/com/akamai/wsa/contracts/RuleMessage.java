package com.akamai.wsa.contracts;

public record RuleMessage(
        String id,
        String name,
        String message,
        Severity severity,
        AttackCategory category
) {
}
