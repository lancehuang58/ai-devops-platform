package com.delta.dms.ops.aidevopsplatform.model.workitem;

/** Display-only marker. It is not a status. */
public enum BlockedReason {
    LLM_UNAVAILABLE,
    KILL_SWITCH,
    RESOURCE_HOLD,
    RESOURCE_LOCK_WAIT,
    JIRA_SYNC_PENDING
}
