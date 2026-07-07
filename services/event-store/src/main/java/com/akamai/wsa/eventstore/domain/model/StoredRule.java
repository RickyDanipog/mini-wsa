package com.akamai.wsa.eventstore.domain.model;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;

public record StoredRule(String id, String name, String message, Severity severity, AttackCategory category) {
}
