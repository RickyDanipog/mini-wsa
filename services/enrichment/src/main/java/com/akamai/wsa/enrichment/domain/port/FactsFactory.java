package com.akamai.wsa.enrichment.domain.port;

import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.ruleengine.Facts;

import java.util.Set;

public interface FactsFactory {

    Facts create(RawEventMessage event, long offenderEventCount);

    Set<String> availableFactKeys();
}
