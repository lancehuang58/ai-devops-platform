package com.delta.dms.ops.aidevopsplatform.model.remediation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnownFixPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");
    private final KnownFixPolicy policy = new KnownFixPolicy();

    @Test void publishedMatchOutsideTheWhitelistNeedsApproval() {
        var decision = policy.decide(context(), List.of(solution(true)), List.of(), List.of());
        assertThat(decision.outcome()).isEqualTo(MatchOutcome.MATCHED);
        assertThat(decision.autoExecute()).isFalse();
        assertThat(decision.solutionRef()).isEqualTo("disk-cleanup@1");
    }

    @Test void enabledWhitelistRuleAutoExecutesUntilTheGroupOrResourceCap() {
        var rule = new AutoRemediationRule("rule-1", "disk-cleanup", "1", "DISK_USAGE_HIGH", "PROD", "vm-1", true, 1, 3, Duration.ofHours(24));
        var auto = policy.decide(context(), List.of(solution(true)), List.of(rule), List.of());
        assertThat(auto.autoExecute()).isTrue();
        assertThat(auto.authorizationSource()).isEqualTo("AUTO_REMEDIATION_RULE");

        var priorGroup = List.of(new RemediationRun("fp-1", "vm-1", NOW.minusSeconds(10)));
        var blockedGroup = policy.decide(context(), List.of(solution(true)), List.of(rule), priorGroup);
        assertThat(blockedGroup.autoExecute()).isFalse();
        assertThat(blockedGroup.outcome()).isEqualTo(MatchOutcome.MATCHED);

        var runs = List.of(
                new RemediationRun("other-a", "vm-1", NOW.minus(Duration.ofHours(1))),
                new RemediationRun("other-b", "vm-1", NOW.minus(Duration.ofHours(2))),
                new RemediationRun("other-c", "vm-1", NOW.minus(Duration.ofHours(3))));
        assertThat(policy.decide(context(), List.of(solution(true)), List.of(rule), runs).autoExecute()).isFalse();
    }

    @Test void noMatchAndAmbiguousInputStayOutOfAutoExecution() {
        assertThat(policy.decide(context(), List.of(), List.of(), List.of()).outcome()).isEqualTo(MatchOutcome.NO_MATCH);
        var ambiguous = policy.decide(context().withNeedsInput(true), List.of(solution(true)), List.of(), List.of());
        assertThat(ambiguous.outcome()).isEqualTo(MatchOutcome.NEEDS_INPUT);
        assertThat(ambiguous.autoExecute()).isFalse();
        assertThat(policy.decide(context(), List.of(solution(false)), List.of(), List.of()).outcome()).isEqualTo(MatchOutcome.NO_MATCH);
    }

    private static MatchContext context() {
        return new MatchContext("DISK_USAGE_HIGH", "PROD", "vm-1", "fp-1", false);
    }

    private static PublishedSolution solution(boolean published) {
        return new PublishedSolution("disk-cleanup", "1", "DISK_USAGE_HIGH", published);
    }
}
