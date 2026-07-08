package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.infrastructure.rules.DefaultScoringRules;
import com.akamai.wsa.enrichment.infrastructure.rules.InMemoryScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Facts;
import com.akamai.wsa.enrichment.ruleengine.MapFacts;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleCondition;
import com.akamai.wsa.enrichment.ruleengine.RuleOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRulesScoringTest {

    private final ThreatScoreCalculator calculator = new RuleEngineThreatScoreCalculator(
            new InMemoryScoringRuleRepository());

    private static ScoringRuleRepository repositoryReturning(List<Rule<Integer>> rules) {
        return new ScoringRuleRepository() {
            @Override
            public List<Rule<Integer>> findEnabledRules() {
                return rules;
            }

            @Override
            public List<Rule<Integer>> findAll() {
                return rules;
            }

            @Override
            public Rule<Integer> save(Rule<Integer> rule) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void deleteById(String id) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private Facts facts(String severity, String action, String path, long offenderEventCount) {
        return facts(severity, action, path, offenderEventCount, null);
    }

    private Facts facts(String severity, String action, String path, long offenderEventCount, String country) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("severity", severity);
        Map<String, Object> values = new HashMap<>();
        values.put("rule", rule);
        values.put("action", action);
        values.put("path", path);
        values.put("offenderEventCount", offenderEventCount);
        if (country != null) {
            Map<String, Object> geoLocation = new HashMap<>();
            geoLocation.put("country", country);
            values.put("geoLocation", geoLocation);
        }
        return new MapFacts(values);
    }

    private int score(String severity, String action, String path, long offenderEventCount) {
        return calculator.calculate(facts(severity, action, path, offenderEventCount)).value();
    }

    @Test
    void reproducesHighAndLowMatrixCorners() {
        assertThat(score("CRITICAL", "DENY", "/api/v1/login", 6)).isEqualTo(90);
        assertThat(score("MEDIUM", "MONITOR", "/search", 0)).isEqualTo(20);
    }

    @Test
    void scoresEverySeverityTier() {
        assertThat(score("CRITICAL", "MONITOR", "/x", 0)).isEqualTo(40);
        assertThat(score("HIGH", "MONITOR", "/x", 0)).isEqualTo(30);
        assertThat(score("MEDIUM", "MONITOR", "/x", 0)).isEqualTo(20);
        assertThat(score("LOW", "MONITOR", "/x", 0)).isEqualTo(10);
    }

    @Test
    void scoresEveryAction() {
        assertThat(score("LOW", "DENY", "/x", 0)).isEqualTo(30);
        assertThat(score("LOW", "ALERT", "/x", 0)).isEqualTo(20);
        assertThat(score("LOW", "MONITOR", "/x", 0)).isEqualTo(10);
    }

    @Test
    void monitorActionAddsNothing() {
        assertThat(score("MEDIUM", "MONITOR", "/x", 0)).isEqualTo(20);
    }

    @Test
    void sensitivePathAddsFifteenForAdminAndLoginOnly() {
        assertThat(score("LOW", "MONITOR", "/admin", 0)).isEqualTo(25);
        assertThat(score("LOW", "MONITOR", "/api/v1/login", 0)).isEqualTo(25);
        assertThat(score("LOW", "MONITOR", "/admin/users", 0)).isEqualTo(25);
        assertThat(score("LOW", "MONITOR", "/public/home", 0)).isEqualTo(10);
    }

    @Test
    void repeatOffenderTriggersOnlyAboveFive() {
        assertThat(score("LOW", "MONITOR", "/x", 5)).isEqualTo(10);
        assertThat(score("LOW", "MONITOR", "/x", 6)).isEqualTo(25);
    }

    @Test
    void totalIsCappedAtOneHundred() {
        ScoringRuleRepository inflatedRepository = repositoryReturning(List.of(
                new Rule<>("big", ScoringRuleRepository.SCORING_TYPE, "Big", 10, true,
                        new RuleCondition(FactKey.SEVERITY, RuleOperator.EQUAL_TO, "CRITICAL"), 70),
                new Rule<>("bigger", ScoringRuleRepository.SCORING_TYPE, "Bigger", 20, true,
                        new RuleCondition(FactKey.ACTION, RuleOperator.EQUAL_TO, "DENY"), 50)));
        ThreatScoreCalculator overflowing = new RuleEngineThreatScoreCalculator(inflatedRepository);

        int total = overflowing.calculate(facts("CRITICAL", "DENY", "/x", 0)).value();

        assertThat(total).isEqualTo(100);
    }

    @Test
    void nestedGeoCountryRuleContributesThroughDottedPath() {
        List<Rule<Integer>> rules = new ArrayList<>(DefaultScoringRules.asList());
        rules.add(new Rule<>("geo-cn", ScoringRuleRepository.SCORING_TYPE, "China origin", 50, true,
                new RuleCondition(FactKey.GEO_COUNTRY, RuleOperator.EQUAL_TO, "CN"), 5));
        ThreatScoreCalculator withGeo = new RuleEngineThreatScoreCalculator(repositoryReturning(rules));

        int withoutCountry = withGeo.calculate(facts("LOW", "MONITOR", "/x", 0, null)).value();
        int withChina = withGeo.calculate(facts("LOW", "MONITOR", "/x", 0, "CN")).value();

        assertThat(withoutCountry).isEqualTo(10);
        assertThat(withChina).isEqualTo(15);
    }
}
