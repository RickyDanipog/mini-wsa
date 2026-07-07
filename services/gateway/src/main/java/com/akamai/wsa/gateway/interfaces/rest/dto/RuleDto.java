package com.akamai.wsa.gateway.interfaces.rest.dto;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Inbound representation of the security rule that matched a request. */
public record RuleDto(
        @NotBlank String id,
        @NotBlank String name,
        String message,
        @NotNull Severity severity,
        @NotNull AttackCategory category
) {
}
