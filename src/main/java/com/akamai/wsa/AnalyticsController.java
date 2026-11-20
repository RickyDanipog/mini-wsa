package com.akamai.wsa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AnalyticsController {
    private final EventAnalytics analytics;
    private final Clock clock;

    AnalyticsController(EventAnalytics analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @GetMapping("/v1/stats/summary")
    EventAnalytics.Summary summary(@RequestParam(required = false) Integer configId,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to) {
        return analytics.summary(configId, range(from, to));
    }

    @GetMapping("/v1/events/samples")
    EventAnalytics.Samples samples(@RequestParam(required = false) Integer configId,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to,
                                   @RequestParam(required = false) SecurityEvent.Category category,
                                   @RequestParam(required = false) SecurityEvent.Action action,
                                   @RequestParam(defaultValue = "20") int limit,
                                   @RequestParam(defaultValue = "0") long offset) {
        return analytics.samples(configId, range(from, to), category, action, Math.clamp(limit, 1, 100),
                Math.max(offset, 0));
    }

    @GetMapping("/v1/stats/timeseries")
    EventAnalytics.TimeSeries timeSeries(@RequestParam(required = false) Integer configId,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(defaultValue = "1m") String interval) {
        int seconds = switch (interval) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "1h" -> 3600;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected 1m, 5m, or 1h");
        };
        return analytics.timeSeries(configId, range(from, to), interval, seconds);
    }

    @PostMapping("/v1/alerts/define")
    @ResponseStatus(HttpStatus.CREATED)
    EventAnalytics.AlertRule define(@Valid @RequestBody AlertRequest request) {
        return analytics.defineAlert(request.category(), request.threshold(), request.windowMinutes());
    }

    @GetMapping("/v1/alerts/evaluate")
    EventAnalytics.AlertEvaluations evaluate() {
        return analytics.evaluateAlerts(clock.instant());
    }

    private static EventAnalytics.TimeRange range(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be at or before to");
        }
        return new EventAnalytics.TimeRange(from, to);
    }

    record AlertRequest(@NotNull SecurityEvent.Category category, @NotNull @PositiveOrZero Integer threshold,
                        @NotNull @Positive Integer windowMinutes) {}
}
