package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.AnalyticsQueryService;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/events")
public class SamplesController {

    private final AnalyticsQueryService analyticsQueryService;

    public SamplesController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/samples")
    public EventSamplesResponse samples(
            @RequestParam(required = false) Integer configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AttackCategory category,
            @RequestParam(required = false) Action action,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        int effectiveLimit = SampleQuery.clampLimit(limit);
        int effectiveOffset = Math.max(0, offset);
        SampleQuery query = new SampleQuery(
                configId, new TimeRange(from, to), category, action, effectiveLimit, effectiveOffset);
        EventSamplesPage page = analyticsQueryService.findSamples(query);
        return EventSamplesResponse.from(page);
    }
}
