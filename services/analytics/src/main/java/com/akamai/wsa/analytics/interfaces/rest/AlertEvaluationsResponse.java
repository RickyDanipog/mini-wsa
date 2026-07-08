package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.domain.alert.AlertEvaluation;

import java.time.Instant;
import java.util.List;

public record AlertEvaluationsResponse(Instant asOf, List<Alert> alerts) {

    public record Window(Instant from, Instant to) {
    }

    public record Alert(
            String ruleId,
            String category,
            int threshold,
            int windowMinutes,
            long count,
            boolean firing,
            Window window) {
    }

    public static AlertEvaluationsResponse from(Instant asOf, List<AlertEvaluation> evaluations) {
        List<Alert> alerts = evaluations.stream()
                .map(evaluation -> new Alert(
                        evaluation.ruleId(),
                        evaluation.category().name(),
                        evaluation.threshold(),
                        evaluation.windowMinutes(),
                        evaluation.count(),
                        evaluation.firing(),
                        new Window(evaluation.window().from(), evaluation.window().to())))
                .toList();
        return new AlertEvaluationsResponse(asOf, alerts);
    }
}
