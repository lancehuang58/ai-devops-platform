package com.delta.dms.ops.aidevopsplatform.model.remediation;

import java.time.Instant;
import java.util.List;

public final class KnownFixPolicy {
    public RemediationDecision decide(MatchContext context, List<PublishedSolution> solutions,
                                      List<AutoRemediationRule> rules, List<RemediationRun> runs, Instant now) {
        if (context.needsInput()) return new RemediationDecision(MatchOutcome.NEEDS_INPUT, null, false, null);
        var matches = solutions.stream()
                .filter(PublishedSolution::published)
                .filter(solution -> solution.symptomType().equals(context.symptomType()))
                .toList();
        if (matches.isEmpty()) return RemediationDecision.none();
        if (matches.size() > 1) return new RemediationDecision(MatchOutcome.NEEDS_INPUT, null, false, null);
        var solution = matches.getFirst();
        var selected = rules.stream().filter(rule -> matches(rule, solution, context)).findFirst();
        if (selected.isEmpty()) {
            return new RemediationDecision(MatchOutcome.MATCHED, solution.ref(), false, "USER_APPROVAL");
        }
        var rule = selected.get();
        long groupRuns = runs.stream().filter(run -> run.fingerprint().equals(context.fingerprint())).count();
        var windowStart = now.minus(rule.window());
        long resourceRuns = runs.stream()
                .filter(run -> run.resourceId().equals(context.resourceId()))
                .filter(run -> !run.ranAt().isBefore(windowStart))
                .count();
        boolean allowed = groupRuns < rule.maxPerGroup() && resourceRuns < rule.maxPerResource();
        return new RemediationDecision(MatchOutcome.MATCHED, solution.ref(), allowed,
                allowed ? "AUTO_REMEDIATION_RULE" : "USER_APPROVAL");
    }

    private static boolean matches(AutoRemediationRule rule, PublishedSolution solution, MatchContext context) {
        return rule.enabled()
                && rule.solutionId().equals(solution.solutionId())
                && rule.version().equals(solution.version())
                && rule.symptomType().equals(context.symptomType())
                && rule.environment().equals(context.environment())
                && rule.resourceId().equals(context.resourceId());
    }
}
