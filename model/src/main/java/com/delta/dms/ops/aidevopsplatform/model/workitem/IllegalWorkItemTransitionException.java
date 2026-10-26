package com.delta.dms.ops.aidevopsplatform.model.workitem;

public final class IllegalWorkItemTransitionException extends RuntimeException {
    private final WorkItemStatus from;
    private final WorkItemStatus to;

    public IllegalWorkItemTransitionException(WorkItemStatus from, WorkItemStatus to) {
        super("Illegal transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public WorkItemStatus from() { return from; }
    public WorkItemStatus to() { return to; }
}
