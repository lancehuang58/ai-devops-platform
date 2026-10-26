package com.delta.dms.ops.aidevopsplatform.model.workitem;

import java.time.Instant;
import java.util.UUID;

public record WorkItemSnapshot(
        UUID itemId,
        String itemNo,
        ItemType itemType,
        WorkItemStatus status,
        ClosureReason closureReason,
        ClosureReason suggestedClosureReason,
        String title,
        String requestorPrincipalId,
        SourceKind sourceKind,
        String environment,
        BlockedReason blockedReason,
        UUID activeTakeoverId,
        UUID duplicateOf,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        Instant statusChangedAt) {
}
