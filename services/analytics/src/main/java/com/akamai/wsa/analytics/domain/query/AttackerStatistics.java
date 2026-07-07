package com.akamai.wsa.analytics.domain.query;

public record AttackerStatistics(String clientIp, long count, double averageThreatScore) {
}
