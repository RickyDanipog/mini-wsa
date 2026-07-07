package com.akamai.wsa.enrichment.domain.model;

import com.akamai.wsa.contracts.AttackCategory;

/**
 * Human-readable attack classification derived from a raw event's rule category.
 * The wire form on {@code events.enriched} is the {@link #displayName()} String,
 * so this enum is enrichment-local (there is no {@code contracts.AttackType}).
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
