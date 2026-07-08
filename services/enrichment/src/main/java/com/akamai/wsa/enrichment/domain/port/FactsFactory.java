package com.akamai.wsa.enrichment.domain.port;

import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.ruleengine.Facts;

public interface FactsFactory {

    Facts create(RawEventMessage event, long offenderEventCount);
}
