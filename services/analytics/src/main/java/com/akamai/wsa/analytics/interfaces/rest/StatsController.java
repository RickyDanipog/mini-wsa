package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.AnalyticsQueryService;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/stats")
public class StatsController {

    private final AnalyticsQueryService analyticsQueryService;

    public StatsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/summary")
    public StatisticsSummaryResponse summary(
            @RequestParam(required = false) Integer configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        StatisticsQuery query = new StatisticsQuery(configId, new TimeRange(from, to));
        StatisticsSummary summary = analyticsQueryService.summarize(query);
        return StatisticsSummaryResponse.from(summary);
    }
}
