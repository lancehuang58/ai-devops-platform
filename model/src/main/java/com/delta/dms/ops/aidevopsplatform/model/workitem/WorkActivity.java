package com.delta.dms.ops.aidevopsplatform.model.workitem;

import java.time.Instant;
import java.util.UUID;

public record WorkActivity(UUID activityId, ActivityKind kind, Instant startedAt, Instant finishedAt) {
    public WorkActivity {
        if (activityId == null || kind == null || startedAt == null) throw new IllegalArgumentException("activity requires id, kind and start");
    }
}
