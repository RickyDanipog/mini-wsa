package com.akamai.wsa.domain.model;

/**
 * The human-readable classification of an attack, derived from {@link AttackCategory}.
 * The display name is the exact string the assignment requires in the enriched event.
 */
public enum AttackType {
    SQL_COMMAND_INJECTION("SQL/Command Injection"),
    CROSS_SITE_SCRIPTING("Cross-Site Scripting"),
    PROTOCOL_ANOMALY("Protocol Anomaly"),
    DATA_EXFILTRATION("Data Exfiltration"),
    BOT_ACTIVITY("Bot Activity"),
    DENIAL_OF_SERVICE("Denial of Service"),
    RATE_LIMITING("Rate Limiting");

    private final String displayName;

    AttackType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static AttackType fromCategory(AttackCategory attackCategory) {
        return switch (attackCategory) {
            case INJECTION -> SQL_COMMAND_INJECTION;
            case XSS -> CROSS_SITE_SCRIPTING;
            case PROTOCOL_VIOLATION -> PROTOCOL_ANOMALY;
            case DATA_LEAKAGE -> DATA_EXFILTRATION;
            case BOT -> BOT_ACTIVITY;
            case DOS -> DENIAL_OF_SERVICE;
            case RATE_LIMIT -> RATE_LIMITING;
        };
    }
}
