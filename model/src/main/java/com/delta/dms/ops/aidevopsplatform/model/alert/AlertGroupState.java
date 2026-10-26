package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.time.Instant;

public record AlertGroupState(
        GroupStatus status,
        Severity severityCurrent,
        Severity severityMax,
        boolean flapping,
        Instant flapWindowStart,
        int flapTransitions,
        long occurrenceCount,
        long suppressedCount,
        Instant lastStateChangeAt,
        Instant recoveringSince,
        Instant reopenUntil,
        int epoch) {
}
