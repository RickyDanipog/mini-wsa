package com.akamai.wsa.application.stats;

/**
 * Per-category rollup: how many events and their average threat score.
 */
public record CategoryStatistics(long count, double averageThreatScore) {
}
