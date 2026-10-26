package com.delta.dms.ops.aidevopsplatform.model.workitem;

import java.time.Instant;
import java.util.UUID;

/** Append-only fact about a work item. Not an authorization to execute. */
public record CaseEvent(UUID eventId, UUID itemId, String type, String detail, Instant occurredAt, long aggregateVersion) {
    public CaseEvent {
        if (eventId == null || itemId == null || type == null || type.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("case event requires id, item, type and time");
        }
    }
}
