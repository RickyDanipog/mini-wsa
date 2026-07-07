package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;

import java.util.List;

public record EventSamplesResponse(long total, int limit, int offset, List<SecurityEventResponse> results) {

    public static EventSamplesResponse from(EventSamplesPage page) {
        List<SecurityEventResponse> results = page.events().stream()
                .map(SecurityEventResponse::from)
                .toList();
        return new EventSamplesResponse(page.total(), page.limit(), page.offset(), results);
    }
}
