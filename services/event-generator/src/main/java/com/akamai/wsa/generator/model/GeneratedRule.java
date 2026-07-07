package com.akamai.wsa.generator.model;

/** Mirrors {@code contracts.RuleMessage} (enum names carried as Strings on the wire). */
public record GeneratedRule(String id, String name, String message, String severity, String category) {
}
