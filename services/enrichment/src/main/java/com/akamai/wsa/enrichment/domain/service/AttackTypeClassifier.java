package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;

/** Maps a rule's {@link AttackCategory} to its human-readable display name. */
public interface AttackTypeClassifier {
    String displayNameFor(AttackCategory attackCategory);
}
