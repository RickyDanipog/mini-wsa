package com.akamai.wsa.application.stats;

import com.akamai.wsa.domain.model.ClientIp;

/**
 * A top-attacker row: the client, its event count, and average threat score.
 */
public record AttackerStatistics(ClientIp clientIp, long count, double averageThreatScore) {
}
