package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.alert.AlertService;
import com.akamai.wsa.analytics.domain.alert.AlertEvaluation;
import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.contracts.AttackCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/v1/alerts")
public class AlertsController {

    private final AlertService alertService;
    private final Clock clock;

    public AlertsController(AlertService alertService, Clock clock) {
        this.alertService = alertService;
        this.clock = clock;
    }

    @PostMapping("/define")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRuleResponse define(@RequestBody DefineAlertRuleRequest request) {
        AlertRule alertRule = alertService.defineRule(
                parseCategory(request.category()), request.threshold(), request.windowMinutes());
        return AlertRuleResponse.from(alertRule);
    }

    @GetMapping("/evaluate")
    public AlertEvaluationsResponse evaluate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {
        Instant effectiveAsOf = asOf != null ? asOf : Instant.now(clock);
        List<AlertEvaluation> evaluations = alertService.evaluate(effectiveAsOf);
        return AlertEvaluationsResponse.from(effectiveAsOf, evaluations);
    }

    private static AttackCategory parseCategory(String category) {
        try {
            return AttackCategory.valueOf(category);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "invalid category: " + category + " (allowed: " + Arrays.toString(AttackCategory.values()) + ")");
        }
    }
}
