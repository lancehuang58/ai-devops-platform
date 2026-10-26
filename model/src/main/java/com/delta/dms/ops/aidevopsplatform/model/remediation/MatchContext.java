package com.delta.dms.ops.aidevopsplatform.model.remediation;

public record MatchContext(String symptomType, String environment, String resourceId, String fingerprint, boolean needsInput) {
    public MatchContext withNeedsInput(boolean value) {
        return new MatchContext(symptomType, environment, resourceId, fingerprint, value);
    }
}
