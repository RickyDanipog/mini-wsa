package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.domain.port.FactsFactory;
import com.akamai.wsa.enrichment.domain.service.FactKey;
import com.akamai.wsa.enrichment.ruleengine.Facts;
import com.akamai.wsa.enrichment.ruleengine.MapFacts;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class JacksonFactsFactory implements FactsFactory {

    private final ObjectMapper objectMapper;

    public JacksonFactsFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Facts create(RawEventMessage event, long offenderEventCount) {
        Map<String, Object> values = objectMapper.convertValue(event, new TypeReference<Map<String, Object>>() {
        });
        Map<String, Object> withDerived = new HashMap<>(values);
        withDerived.put(FactKey.OFFENDER_EVENT_COUNT, offenderEventCount);
        return new MapFacts(withDerived);
    }
}
