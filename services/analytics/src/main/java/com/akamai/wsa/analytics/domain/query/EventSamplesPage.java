package com.akamai.wsa.analytics.domain.query;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;

import java.util.List;

public record EventSamplesPage(long total, int limit, int offset, List<EnrichedEventView> events) {
}
