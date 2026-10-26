package com.delta.dms.ops.aidevopsplatform.service.workflow;

import java.time.Instant;
import java.util.List;

public record WorkItemDetail(String itemNo, String itemType, String status, String title, String environment,
                              String resourceId, String closureReason, String suggestedClosureReason,
                              String blockedReason, String requestor, boolean activeTakeover, Instant updatedAt,
                              List<TimelineEvent> timeline) {
}
