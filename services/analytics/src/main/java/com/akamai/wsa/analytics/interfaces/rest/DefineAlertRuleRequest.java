package com.akamai.wsa.analytics.interfaces.rest;

public record DefineAlertRuleRequest(String category, int threshold, int windowMinutes) {
}
