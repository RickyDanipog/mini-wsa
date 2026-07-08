package com.akamai.wsa.enrichment.interfaces.rest;

import java.util.List;

public record ScoringRuleOptionsResponse(List<String> operators, List<String> factKeys) {
}
