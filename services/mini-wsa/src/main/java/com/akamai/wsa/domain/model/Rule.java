package com.akamai.wsa.domain.model;

/**
 * The security rule that matched a request (the nested "rule" object on a DLR).
 */
public record Rule(
        String id,
        String name,
        String message,
        Severity severity,
        AttackCategory category
) {
    public Rule {
        if (severity == null) {
            throw new IllegalArgumentException("rule.severity must not be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("rule.category must not be null");
        }
    }
}
