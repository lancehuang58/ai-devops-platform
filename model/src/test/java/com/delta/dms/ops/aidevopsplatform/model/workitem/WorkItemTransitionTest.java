package com.delta.dms.ops.aidevopsplatform.model.workitem;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkItemTransitionTest {
    private static final Instant T0 = Instant.parse("2026-10-26T01:00:00Z");

    @Test void newIncidentFollowsTheSingleStateMachineThroughConfirmedClosure() {
        var item = incident();
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.NEW);
        assertThat(item.snapshot().closureReason()).isNull();
        assertThat(item.snapshot().rowVersion()).isZero();

        item.beginCollecting(T0.plusSeconds(1), event());
        item.beginMatching(T0.plusSeconds(2), event());
        item.noMatch(T0.plusSeconds(3), event());
        item.submitProposal(T0.plusSeconds(4), event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PENDING_APPROVAL);

        item.approve(T0.plusSeconds(5), event());
        item.changeAccepted(T0.plusSeconds(6), event());
        item.verificationPassed(ClosureReason.RESOLVED, T0.plusSeconds(7), event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSURE_REVIEW);
        assertThat(item.snapshot().closureReason()).isNull();

        item.confirmClosure(ClosureReason.RESOLVED, null, T0.plusSeconds(8), event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSED);
        assertThat(item.snapshot().closureReason()).isEqualTo(ClosureReason.RESOLVED);
        assertThat(item.pendingEvents()).allMatch(event -> event.itemId().equals(item.snapshot().itemId()));
        assertThat(item.pendingEvents()).extracting(CaseEvent::type).contains("STATE_CHANGED");
        assertThat(item.activities()).extracting(WorkActivity::kind)
                .contains(ActivityKind.INVESTIGATION, ActivityKind.MATCHING, ActivityKind.PROPOSAL,
                        ActivityKind.APPLY, ActivityKind.VERIFY, ActivityKind.CLOSURE);
    }

    @Test void whitelistMatchSkipsApprovalAndStillRequiresClosureConfirmation() {
        var item = incident();
        item.beginCollecting(T0, event());
        item.beginMatching(T0, event());
        item.matchedAutoRemediate(T0, event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.APPLYING);
        item.changeAccepted(T0, event());
        item.verificationPassed(ClosureReason.RESOLVED, T0, event());
        assertThatThrownBy(() -> item.confirmClosure(null, null, T0, event()))
                .isInstanceOf(IllegalWorkItemTransitionException.class);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSURE_REVIEW);
    }

    @Test void unansweredApprovalStaysPending() {
        var item = incident();
        toApproval(item);
        var version = item.snapshot().rowVersion();
        assertThatThrownBy(() -> item.confirmClosure(ClosureReason.RESOLVED, null, T0.plusSeconds(86400), event()))
                .isInstanceOf(IllegalWorkItemTransitionException.class);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PENDING_APPROVAL);
        assertThat(item.snapshot().rowVersion()).isEqualTo(version);
    }

    @Test void illegalTransitionLeavesStateAndHistoryUnchanged() {
        var item = incident();
        var before = item.snapshot();
        var events = item.pendingEvents().size();
        assertThatThrownBy(() -> item.changeAccepted(T0, event()))
                .isInstanceOf(IllegalWorkItemTransitionException.class)
                .hasMessageContaining("NEW")
                .hasMessageContaining("VERIFYING");
        assertThat(item.snapshot()).isEqualTo(before);
        assertThat(item.pendingEvents()).hasSize(events);
    }

    @Test void discussionCannotApplyOrVerify() {
        var item = WorkItem.open(UUID.randomUUID(), "OPS-0000000002", ItemType.DISCUSSION, "compare options",
                "ada", SourceKind.USER, "PROD", T0, event());
        item.beginCollecting(T0, event());
        item.beginMatching(T0, event());
        item.matchedRequiresApproval(T0, event());
        assertThatThrownBy(() -> item.approve(T0, event())).isInstanceOf(IllegalWorkItemTransitionException.class);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PENDING_APPROVAL);
        item.withdraw(ClosureReason.CONCLUDED, T0, event());
        item.confirmClosure(ClosureReason.CONCLUDED, null, T0, event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSED);
    }

    @Test void inquiryCannotEnterMatchingUntilItBecomesAnIncident() {
        var item = WorkItem.open(UUID.randomUUID(), "OPS-0000000003", ItemType.INQUIRY, "check jenkins",
                "ada", SourceKind.USER, "UAT", T0, event());
        item.beginCollecting(T0, event());
        assertThatThrownBy(() -> item.beginMatching(T0, event())).isInstanceOf(IllegalWorkItemTransitionException.class);
        item.changeType(ItemType.INCIDENT, "found an outage", T0, event());
        assertThat(item.snapshot().itemType()).isEqualTo(ItemType.INCIDENT);
        assertThat(item.snapshot().itemId()).isEqualTo(item.snapshot().itemId());
        item.beginMatching(T0, event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.MATCHING);
        assertThat(item.pendingEvents()).extracting(CaseEvent::type).contains("ITEM_TYPE_CHANGED");
    }

    @Test void selfRecoveredSignalMovesEarlyIncidentToClosureReviewWithoutClosing() {
        var item = incident();
        item.beginCollecting(T0, event());
        item.signalRecovered(T0, event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSURE_REVIEW);
        assertThat(item.snapshot().suggestedClosureReason()).isEqualTo(ClosureReason.SELF_RECOVERED);
        assertThat(item.snapshot().closureReason()).isNull();
    }

    @Test void forcedCloseRequiresAnActiveTakeoverAndSkipsClosureReview() {
        var item = incident();
        item.beginCollecting(T0, event());
        assertThatThrownBy(() -> item.forceClose(T0, event()))
                .isInstanceOf(IllegalWorkItemTransitionException.class);
        item.takeover(UUID.randomUUID(), T0, event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PENDING_HUMAN);
        assertThat(item.snapshot().activeTakeoverId()).isNotNull();
        item.forceClose(T0, event());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSED);
        assertThat(item.snapshot().closureReason()).isEqualTo(ClosureReason.FORCED_BY_COMMERCIAL_MODEL);
    }

    @Test void confirmClosureRejectsForcedReasonAndDuplicateWithoutTarget() {
        var item = incident();
        item.beginCollecting(T0, event());
        item.signalRecovered(T0, event());
        assertThatThrownBy(() -> item.confirmClosure(ClosureReason.FORCED_BY_COMMERCIAL_MODEL, null, T0, event()))
                .isInstanceOf(IllegalWorkItemTransitionException.class);
        assertThatThrownBy(() -> item.confirmClosure(ClosureReason.DUPLICATE, null, T0, event()))
                .isInstanceOf(IllegalWorkItemTransitionException.class);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSURE_REVIEW);
        item.confirmClosure(ClosureReason.DUPLICATE, UUID.randomUUID(), T0, event());
        assertThat(item.snapshot().duplicateOf()).isNotNull();
    }

    @Test void closedItemRejectsFurtherTransitions() {
        var item = incident();
        driveToClosed(item);
        var version = item.snapshot().rowVersion();
        assertThatThrownBy(() -> item.beginCollecting(T0, event())).isInstanceOf(IllegalWorkItemTransitionException.class);
        assertThat(item.snapshot().rowVersion()).isEqualTo(version);
    }

    @Test void rehydrateDoesNotReplayCreation() {
        var item = incident();
        item.beginCollecting(T0, event());
        var restored = WorkItem.rehydrate(item.snapshot());
        assertThat(restored.pendingEvents()).isEmpty();
        assertThat(restored.snapshot().status()).isEqualTo(WorkItemStatus.COLLECTING);
        restored.beginMatching(T0, event());
        assertThat(restored.snapshot().status()).isEqualTo(WorkItemStatus.MATCHING);
    }

    private static WorkItem incident() {
        return WorkItem.open(UUID.randomUUID(), "OPS-0000000001", ItemType.INCIDENT, "disk full",
                "monitor", SourceKind.MONITOR, "PROD", T0, event());
    }

    private static void toApproval(WorkItem item) {
        item.beginCollecting(T0, event());
        item.beginMatching(T0, event());
        item.matchedRequiresApproval(T0, event());
    }

    private static void driveToClosed(WorkItem item) {
        item.beginCollecting(T0, event());
        item.signalRecovered(T0, event());
        item.confirmClosure(ClosureReason.SELF_RECOVERED, null, T0, event());
    }

    private static UUID event() { return UUID.randomUUID(); }
}
