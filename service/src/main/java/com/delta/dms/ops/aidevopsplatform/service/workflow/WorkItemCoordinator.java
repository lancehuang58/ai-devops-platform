package com.delta.dms.ops.aidevopsplatform.service.workflow;

import com.delta.dms.ops.aidevopsplatform.model.remediation.KnownFixPolicy;
import com.delta.dms.ops.aidevopsplatform.model.remediation.MatchContext;
import com.delta.dms.ops.aidevopsplatform.model.remediation.MatchOutcome;
import com.delta.dms.ops.aidevopsplatform.model.workitem.BlockedReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItem;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemStatus;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChangeExecutionPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.LlmPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ResourceLockPort;

import java.time.Instant;
import java.util.UUID;

/** One step of the process manager. Waiting states return without holding a worker. */
public final class WorkItemCoordinator {
    private final KnownFixPolicy fixes = new KnownFixPolicy();

    public void advance(WorkItem item, MatchContext match, CollectedEvidence evidence, FixCatalog catalog,
                        LlmPort llm, ChangeExecutionPort execution, ResourceLockPort locks, Instant now, UUID eventId) {
        switch (item.snapshot().status()) {
            case NEW -> item.beginCollecting(now, eventId);
            case COLLECTING -> {
                if (evidence.needsHuman()) item.requestInput(now, eventId);
                else if (evidence.sufficient()) item.beginMatching(now, eventId);
            }
            case MATCHING -> applyMatch(item, match, catalog, now, eventId);
            case PROPOSING -> propose(item, llm, now, eventId);
            case APPLYING -> applyChange(item, execution, locks, now, eventId);
            case VERIFYING -> {
                if (execution.verify(item.snapshot())) item.verificationPassed(ClosureReason.RESOLVED, now, eventId);
                else item.verificationFailed(now, eventId);
            }
            case PENDING_APPROVAL, WAITING_INPUT, CLOSURE_REVIEW, PENDING_HUMAN, CLOSED -> {
                // persisted wait: approval, input, closure confirmation, or human takeover
            }
        }
    }

    private void applyMatch(WorkItem item, MatchContext match, FixCatalog catalog, Instant now, UUID eventId) {
        var decision = fixes.decide(match, catalog.solutions(), catalog.rules(), catalog.runs(), now);
        if (decision.outcome() == MatchOutcome.NEEDS_INPUT) item.matchingNeedsInput(now, eventId);
        else if (decision.outcome() == MatchOutcome.NO_MATCH) item.noMatch(now, eventId);
        else if (decision.autoExecute()) item.matchedAutoRemediate(now, eventId);
        else item.matchedRequiresApproval(now, eventId);
    }

    private void propose(WorkItem item, LlmPort llm, Instant now, UUID eventId) {
        if (!llm.available()) {
            item.markBlocked(BlockedReason.LLM_UNAVAILABLE, now);
            return;
        }
        llm.draftProposal(item.snapshot());
        item.submitProposal(now, eventId);
    }

    private void applyChange(WorkItem item, ChangeExecutionPort execution, ResourceLockPort locks, Instant now, UUID eventId) {
        if (!locks.tryAcquire(item.snapshot())) {
            item.markBlocked(BlockedReason.RESOURCE_LOCK_WAIT, now);
            return;
        }
        if (item.snapshot().blockedReason() == BlockedReason.RESOURCE_LOCK_WAIT) item.clearBlocked(now);
        if (execution.apply(item.snapshot())) item.changeAccepted(now, eventId);
        else item.changeBlocked(now, eventId);
    }

    public WorkItemStatus status(WorkItem item) { return item.snapshot().status(); }
}
