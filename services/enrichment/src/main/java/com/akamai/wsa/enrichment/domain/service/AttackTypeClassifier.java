package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;

public interface AttackTypeClassifier {
    String displayNameFor(AttackCategory attackCategory);
}
