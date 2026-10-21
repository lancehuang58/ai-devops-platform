package com.delta.dms.ops.aidevopsplatform.model.work;

public enum RequestStatus {
    RECEIVED, INVESTIGATING, AWAITING_DECISION, EXECUTING,
    VERIFYING, COMPLETED, PENDING_HUMAN, CANCELLED
}
