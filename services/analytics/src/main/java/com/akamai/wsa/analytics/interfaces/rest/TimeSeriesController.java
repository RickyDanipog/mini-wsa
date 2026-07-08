package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.stats.BuildTimeSeries;
import com.akamai.wsa.analytics.domain.query.Interval;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/stats")
public class TimeSeriesController {

    private final BuildTimeSeries buildTimeSeries;

    public TimeSeriesController(BuildTimeSeries buildTimeSeries) {
        this.buildTimeSeries = buildTimeSeries;
    }

    @GetMapping("/timeseries")
    public TimeSeriesResponse timeSeries(
            @RequestParam(required = false) Integer configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "1h") String interval) {
        TimeSeriesQuery query = new TimeSeriesQuery(
                configId, new TimeRange(from, to), Interval.fromLabel(interval));
        TimeSeriesResult result = buildTimeSeries.build(query);
        return TimeSeriesResponse.from(result);
    }
}
