package com.akamai.wsa.enrichment.interfaces.rest;

import com.akamai.wsa.enrichment.application.rules.ScoringRuleService;
import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.domain.service.FactKey;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleCondition;
import com.akamai.wsa.enrichment.ruleengine.RuleOperator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/v1/rules")
public class ScoringRulesController {

    private final ScoringRuleService scoringRuleService;

    public ScoringRulesController(ScoringRuleService scoringRuleService) {
        this.scoringRuleService = scoringRuleService;
    }

    @GetMapping
    public List<ScoringRuleResponse> list() {
        return scoringRuleService.findAll().stream().map(ScoringRuleResponse::from).toList();
    }

    @GetMapping("/options")
    public ScoringRuleOptionsResponse options() {
        return new ScoringRuleOptionsResponse(
                Arrays.stream(RuleOperator.values()).map(Enum::name).toList(), FactKey.all());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScoringRuleResponse create(@RequestBody ScoringRuleRequest request) {
        return ScoringRuleResponse.from(save(request.id(), request));
    }

    @PutMapping("/{id}")
    public ScoringRuleResponse update(@PathVariable String id, @RequestBody ScoringRuleRequest request) {
        return ScoringRuleResponse.from(save(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        scoringRuleService.delete(id);
    }

    private Rule<Integer> save(String id, ScoringRuleRequest request) {
        Rule<Integer> rule = new Rule<>(id, ScoringRuleRepository.SCORING_TYPE, requireText(request.title(), "title"),
                request.priority(), request.enabled(),
                new RuleCondition(requireText(request.factKey(), "factKey"), parseOperator(request.operator()),
                        requireText(request.operand(), "operand")),
                request.output());
        return scoringRuleService.save(rule);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static RuleOperator parseOperator(String operator) {
        try {
            return RuleOperator.valueOf(operator);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "invalid operator: " + operator + " (allowed: " + Arrays.toString(RuleOperator.values()) + ")");
        }
    }
}
