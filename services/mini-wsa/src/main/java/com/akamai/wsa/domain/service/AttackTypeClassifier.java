package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.AttackType;

/**
 * Maps a machine {@link AttackCategory} to its human-readable {@link AttackType}.
 */
public interface AttackTypeClassifier {

    AttackType classify(AttackCategory attackCategory);
}
