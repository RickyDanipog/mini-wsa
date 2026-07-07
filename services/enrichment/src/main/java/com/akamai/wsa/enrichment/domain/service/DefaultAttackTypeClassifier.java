package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.enrichment.domain.model.AttackType;

public final class DefaultAttackTypeClassifier implements AttackTypeClassifier {
    @Override
    public String displayNameFor(AttackCategory attackCategory) {
        return AttackType.fromCategory(attackCategory).displayName();
    }
}
