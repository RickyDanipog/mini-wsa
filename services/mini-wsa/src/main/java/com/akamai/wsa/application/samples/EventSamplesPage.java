package com.akamai.wsa.application.samples;

import com.akamai.wsa.domain.model.SecurityEvent;

import java.util.List;

/**
 * A page of enriched events plus the total count of events matching the filters
 * (for pagination). Events are ordered by timestamp descending.
 */
public record EventSamplesPage(long total, int limit, int offset, List<SecurityEvent> events) {
}
