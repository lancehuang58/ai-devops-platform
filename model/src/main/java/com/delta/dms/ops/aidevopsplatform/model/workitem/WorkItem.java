package com.delta.dms.ops.aidevopsplatform.model.workitem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Consistency boundary for one work item. Status changes go through the
 * single transition table; a rejected command leaves the snapshot untouched.
 */
public final class WorkItem {
    private static final Set<WorkItemStatus> SELF_RECOVERY_FROM = Set.of(
            WorkItemStatus.COLLECTING, WorkItemStatus.WAITING_INPUT,
            WorkItemStatus.MATCHING, WorkItemStatus.PROPOSING);

    private WorkItemSnapshot snapshot;
    private final List<CaseEvent> events = new ArrayList<>();
    private final List<WorkActivity> activities = new ArrayList<>();

    private WorkItem(WorkItemSnapshot snapshot) { this.snapshot = snapshot; }

    public static WorkItem open(UUID itemId, String itemNo, ItemType type, String title, String requestor,
                                 SourceKind source, String environment, Instant now, UUID eventId) {
        requireText(itemNo, "itemNo");
        requireText(title, "title");
        requireText(requestor, "requestor");
        requireText(environment, "environment");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(eventId, "eventId");
        var opened = new WorkItem(new WorkItemSnapshot(itemId, itemNo, type, WorkItemStatus.NEW, null, null,
                title, requestor, source, environment, null, null, null, 0, now, now, now));
        opened.events.add(new CaseEvent(eventId, itemId, "WORK_ITEM_OPENED", type.name(), now, 0));
        return opened;
    }

    public static WorkItem rehydrate(WorkItemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new WorkItem(snapshot);
    }

    public void beginCollecting(Instant now, UUID eventId) {
        move(WorkItemStatus.COLLECTING, null, null, now, eventId);
        activity(ActivityKind.INVESTIGATION, now);
    }

    public void requestInput(Instant now, UUID eventId) {
        move(WorkItemStatus.WAITING_INPUT, null, null, now, eventId);
        activity(ActivityKind.INPUT, now);
    }

    public void supplyInput(Instant now, UUID eventId) {
        move(WorkItemStatus.COLLECTING, null, null, now, eventId);
    }

    public void beginMatching(Instant now, UUID eventId) {
        move(WorkItemStatus.MATCHING, null, null, now, eventId);
        activity(ActivityKind.MATCHING, now);
    }

    public void matchingNeedsInput(Instant now, UUID eventId) {
        move(WorkItemStatus.WAITING_INPUT, null, null, now, eventId);
    }

    public void matchedRequiresApproval(Instant now, UUID eventId) {
        move(WorkItemStatus.PENDING_APPROVAL, null, null, now, eventId);
    }

    public void matchedAutoRemediate(Instant now, UUID eventId) {
        move(WorkItemStatus.APPLYING, null, null, now, eventId);
        activity(ActivityKind.APPLY, now);
    }

    public void noMatch(Instant now, UUID eventId) {
        move(WorkItemStatus.PROPOSING, null, null, now, eventId);
    }

    public void submitProposal(Instant now, UUID eventId) {
        move(WorkItemStatus.PENDING_APPROVAL, null, null, now, eventId);
        activity(ActivityKind.PROPOSAL, now);
    }

    public void requestMoreEvidence(Instant now, UUID eventId) {
        move(WorkItemStatus.COLLECTING, null, null, now, eventId);
    }

    public void approve(Instant now, UUID eventId) {
        move(WorkItemStatus.APPLYING, null, null, now, eventId);
        activity(ActivityKind.APPLY, now);
    }

    public void returnProposal(Instant now, UUID eventId) {
        move(WorkItemStatus.PROPOSING, null, null, now, eventId);
    }

    public void withdraw(ClosureReason suggested, Instant now, UUID eventId) {
        move(WorkItemStatus.CLOSURE_REVIEW, null, suggested, now, eventId);
        activity(ActivityKind.CLOSURE, now);
    }

    public void changeAccepted(Instant now, UUID eventId) {
        move(WorkItemStatus.VERIFYING, null, null, now, eventId);
        activity(ActivityKind.VERIFY, now);
    }

    public void changeBlocked(Instant now, UUID eventId) {
        move(WorkItemStatus.PENDING_HUMAN, null, null, now, eventId);
    }

    public void verificationPassed(ClosureReason suggested, Instant now, UUID eventId) {
        move(WorkItemStatus.CLOSURE_REVIEW, null, suggested, now, eventId);
        activity(ActivityKind.CLOSURE, now);
    }

    public void verificationFailed(Instant now, UUID eventId) {
        move(WorkItemStatus.PENDING_HUMAN, null, null, now, eventId);
    }

    public void reopenInvestigation(Instant now, UUID eventId) {
        move(WorkItemStatus.COLLECTING, null, null, now, eventId);
    }

    public void requestNewSolution(Instant now, UUID eventId) {
        move(WorkItemStatus.PROPOSING, null, null, now, eventId);
    }

    public void recordManualAndVerify(Instant now, UUID eventId) {
        move(WorkItemStatus.VERIFYING, null, null, now, eventId);
        activity(ActivityKind.VERIFY, now);
    }

    public void decideClose(ClosureReason suggested, Instant now, UUID eventId) {
        move(WorkItemStatus.CLOSURE_REVIEW, null, suggested, now, eventId);
        activity(ActivityKind.CLOSURE, now);
    }

    public void signalRecovered(Instant now, UUID eventId) {
        if (!SELF_RECOVERY_FROM.contains(snapshot.status())) {
            throw new IllegalWorkItemTransitionException(snapshot.status(), WorkItemStatus.CLOSURE_REVIEW);
        }
        move(WorkItemStatus.CLOSURE_REVIEW, null, ClosureReason.SELF_RECOVERED, now, eventId);
        activity(ActivityKind.CLOSURE, now);
    }

    public void rejectClosure(Instant now, UUID eventId) {
        move(WorkItemStatus.COLLECTING, null, null, now, eventId);
    }

    public void confirmClosure(ClosureReason reason, UUID duplicateOf, Instant now, UUID eventId) {
        if (snapshot.status() != WorkItemStatus.CLOSURE_REVIEW
                || reason == null
                || reason == ClosureReason.FORCED_BY_COMMERCIAL_MODEL
                || (reason == ClosureReason.DUPLICATE && duplicateOf == null)) {
            throw new IllegalWorkItemTransitionException(snapshot.status(), WorkItemStatus.CLOSED);
        }
        move(WorkItemStatus.CLOSED, reason, snapshot.suggestedClosureReason(), now, eventId, duplicateOf, snapshot.activeTakeoverId());
    }

    public void takeover(UUID takeoverId, Instant now, UUID eventId) {
        Objects.requireNonNull(takeoverId, "takeoverId");
        if (snapshot.status() == WorkItemStatus.CLOSED) {
            throw new IllegalWorkItemTransitionException(snapshot.status(), WorkItemStatus.PENDING_HUMAN);
        }
        if (snapshot.status() != WorkItemStatus.PENDING_HUMAN) {
            move(WorkItemStatus.PENDING_HUMAN, null, snapshot.suggestedClosureReason(), now, eventId, snapshot.duplicateOf(), takeoverId);
        } else {
            replace(snapshot.status(), snapshot.closureReason(), snapshot.suggestedClosureReason(), now, false, snapshot.duplicateOf(), takeoverId);
            events.add(new CaseEvent(eventId, snapshot.itemId(), "STATE_UNCHANGED", "TAKEOVER", now, snapshot.rowVersion()));
        }
        events.add(new CaseEvent(eventId, snapshot.itemId(), "TAKEOVER_STARTED", takeoverId.toString(), now, snapshot.rowVersion()));
        activity(ActivityKind.TAKEOVER, now);
    }

    public void forceClose(Instant now, UUID eventId) {
        if (snapshot.activeTakeoverId() == null || snapshot.status() == WorkItemStatus.CLOSED) {
            throw new IllegalWorkItemTransitionException(snapshot.status(), WorkItemStatus.CLOSED);
        }
        replace(WorkItemStatus.CLOSED, ClosureReason.FORCED_BY_COMMERCIAL_MODEL, snapshot.suggestedClosureReason(),
                now, true, snapshot.duplicateOf(), snapshot.activeTakeoverId());
        events.add(new CaseEvent(eventId, snapshot.itemId(), "STATE_CHANGED",
                "PENDING_HUMAN->CLOSED", now, snapshot.rowVersion()));
        events.add(new CaseEvent(eventId, snapshot.itemId(), "FORCE_CLOSED",
                ClosureReason.FORCED_BY_COMMERCIAL_MODEL.name(), now, snapshot.rowVersion()));
    }

    public void changeType(ItemType to, String reason, Instant now, UUID eventId) {
        Objects.requireNonNull(to, "type");
        requireText(reason, "reason");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(eventId, "eventId");
        if (snapshot.status() == WorkItemStatus.CLOSED || to == snapshot.itemType()) {
            throw new IllegalWorkItemTransitionException(snapshot.status(), snapshot.status());
        }
        snapshot = new WorkItemSnapshot(snapshot.itemId(), snapshot.itemNo(), to, snapshot.status(),
                snapshot.closureReason(), snapshot.suggestedClosureReason(), snapshot.title(),
                snapshot.requestorPrincipalId(), snapshot.sourceKind(), snapshot.environment(),
                snapshot.blockedReason(), snapshot.activeTakeoverId(), snapshot.duplicateOf(),
                snapshot.rowVersion() + 1, snapshot.createdAt(), now, snapshot.statusChangedAt());
        events.add(new CaseEvent(eventId, snapshot.itemId(), "ITEM_TYPE_CHANGED", reason, now, snapshot.rowVersion()));
    }

    public void markBlocked(BlockedReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(now, "now");
        snapshot = new WorkItemSnapshot(snapshot.itemId(), snapshot.itemNo(), snapshot.itemType(), snapshot.status(),
                snapshot.closureReason(), snapshot.suggestedClosureReason(), snapshot.title(),
                snapshot.requestorPrincipalId(), snapshot.sourceKind(), snapshot.environment(),
                reason, snapshot.activeTakeoverId(), snapshot.duplicateOf(),
                snapshot.rowVersion() + 1, snapshot.createdAt(), now, snapshot.statusChangedAt());
    }

    public void clearBlocked(Instant now) {
        Objects.requireNonNull(now, "now");
        snapshot = new WorkItemSnapshot(snapshot.itemId(), snapshot.itemNo(), snapshot.itemType(), snapshot.status(),
                snapshot.closureReason(), snapshot.suggestedClosureReason(), snapshot.title(),
                snapshot.requestorPrincipalId(), snapshot.sourceKind(), snapshot.environment(),
                null, snapshot.activeTakeoverId(), snapshot.duplicateOf(),
                snapshot.rowVersion() + 1, snapshot.createdAt(), now, snapshot.statusChangedAt());
    }

    public WorkItemSnapshot snapshot() { return snapshot; }
    public List<CaseEvent> pendingEvents() { return List.copyOf(events); }
    public List<WorkActivity> activities() { return List.copyOf(activities); }

    private void move(WorkItemStatus to, ClosureReason closure, ClosureReason suggested, Instant now, UUID eventId) {
        move(to, closure, suggested, now, eventId, snapshot.duplicateOf(), snapshot.activeTakeoverId());
    }

    private void move(WorkItemStatus to, ClosureReason closure, ClosureReason suggested, Instant now, UUID eventId,
                      UUID duplicateOf, UUID takeoverId) {
        var from = snapshot.status();
        if (!legal(from, to) || !typeAllows(snapshot.itemType(), to)) {
            throw new IllegalWorkItemTransitionException(from, to);
        }
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(eventId, "eventId");
        replace(to, to == WorkItemStatus.CLOSED ? closure : snapshot.closureReason(), suggested, now, true, duplicateOf, takeoverId);
        events.add(new CaseEvent(eventId, snapshot.itemId(), "STATE_CHANGED", from + "->" + to, now, snapshot.rowVersion()));
    }

    private void replace(WorkItemStatus status, ClosureReason closure, ClosureReason suggested, Instant now,
                         boolean statusChanged, UUID duplicateOf, UUID takeoverId) {
        snapshot = new WorkItemSnapshot(snapshot.itemId(), snapshot.itemNo(), snapshot.itemType(), status,
                closure, suggested, snapshot.title(), snapshot.requestorPrincipalId(), snapshot.sourceKind(),
                snapshot.environment(), snapshot.blockedReason(), takeoverId, duplicateOf,
                snapshot.rowVersion() + 1, snapshot.createdAt(), now,
                statusChanged ? now : snapshot.statusChangedAt());
    }

    private void activity(ActivityKind kind, Instant now) {
        activities.add(new WorkActivity(UUID.randomUUID(), kind, now, kind == ActivityKind.TAKEOVER ? null : now));
    }

    private static boolean legal(WorkItemStatus from, WorkItemStatus to) {
        if (from == WorkItemStatus.CLOSED) return false;
        if (to == WorkItemStatus.PENDING_HUMAN && from != WorkItemStatus.PENDING_HUMAN) return true;
        return switch (from) {
            case NEW -> to == WorkItemStatus.COLLECTING;
            case COLLECTING -> to == WorkItemStatus.WAITING_INPUT || to == WorkItemStatus.MATCHING
                    || to == WorkItemStatus.CLOSURE_REVIEW;
            case WAITING_INPUT -> to == WorkItemStatus.COLLECTING;
            case MATCHING -> to == WorkItemStatus.WAITING_INPUT || to == WorkItemStatus.PENDING_APPROVAL
                    || to == WorkItemStatus.APPLYING || to == WorkItemStatus.PROPOSING;
            case PROPOSING -> to == WorkItemStatus.PENDING_APPROVAL || to == WorkItemStatus.CLOSURE_REVIEW
                    || to == WorkItemStatus.COLLECTING;
            case PENDING_APPROVAL -> to == WorkItemStatus.APPLYING || to == WorkItemStatus.PROPOSING
                    || to == WorkItemStatus.CLOSURE_REVIEW;
            case APPLYING -> to == WorkItemStatus.VERIFYING || to == WorkItemStatus.PENDING_HUMAN;
            case VERIFYING -> to == WorkItemStatus.CLOSURE_REVIEW || to == WorkItemStatus.PENDING_HUMAN;
            case PENDING_HUMAN -> to == WorkItemStatus.COLLECTING || to == WorkItemStatus.PROPOSING
                    || to == WorkItemStatus.VERIFYING || to == WorkItemStatus.CLOSURE_REVIEW;
            case CLOSURE_REVIEW -> to == WorkItemStatus.CLOSED || to == WorkItemStatus.COLLECTING;
            case CLOSED -> false;
        };
    }

    private static boolean typeAllows(ItemType type, WorkItemStatus to) {
        if (type == ItemType.DISCUSSION && (to == WorkItemStatus.APPLYING || to == WorkItemStatus.VERIFYING)) return false;
        if (type == ItemType.INQUIRY) {
            return to == WorkItemStatus.NEW || to == WorkItemStatus.COLLECTING || to == WorkItemStatus.WAITING_INPUT
                    || to == WorkItemStatus.CLOSURE_REVIEW || to == WorkItemStatus.CLOSED || to == WorkItemStatus.PENDING_HUMAN;
        }
        return true;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
