package com.delta.dms.ops.aidevopsplatform.model.remediation;

import java.time.Duration;

public record AutoRemediationRule(
        String ruleId,
        String solutionId,
        String version,
        String symptomType,
        String environment,
        String resourceId,
        boolean enabled,
        int maxPerGroup,
        int maxPerResource,
        Duration window) {
}
