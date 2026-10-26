package com.delta.dms.ops.aidevopsplatform.model.workitem;

/** Single lifecycle for every work item (aligned requirements §3). */
public enum WorkItemStatus {
    NEW,
    COLLECTING,
    WAITING_INPUT,
    MATCHING,
    PROPOSING,
    PENDING_APPROVAL,
    APPLYING,
    VERIFYING,
    PENDING_HUMAN,
    CLOSURE_REVIEW,
    CLOSED
}
