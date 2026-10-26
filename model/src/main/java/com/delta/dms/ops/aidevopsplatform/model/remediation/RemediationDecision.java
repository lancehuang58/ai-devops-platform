package com.delta.dms.ops.aidevopsplatform.model.remediation;

public record RemediationDecision(MatchOutcome outcome, String solutionRef, boolean autoExecute, String authorizationSource) {
    public static RemediationDecision none() {
        return new RemediationDecision(MatchOutcome.NO_MATCH, null, false, null);
    }
}
