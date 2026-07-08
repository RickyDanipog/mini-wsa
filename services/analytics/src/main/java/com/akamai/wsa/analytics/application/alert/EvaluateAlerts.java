package com.akamai.wsa.analytics.application.alert;

import com.akamai.wsa.analytics.domain.alert.AlertEvaluation;

import java.time.Instant;
import java.util.List;

public interface EvaluateAlerts {
    List<AlertEvaluation> evaluate(Instant asOf);
}
