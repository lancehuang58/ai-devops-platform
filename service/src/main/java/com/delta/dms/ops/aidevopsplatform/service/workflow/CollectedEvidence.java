package com.delta.dms.ops.aidevopsplatform.service.workflow;

public record CollectedEvidence(boolean needsHuman, boolean sufficient) {
    public static CollectedEvidence ready() { return new CollectedEvidence(false, true); }
}
