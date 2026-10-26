package com.delta.dms.ops.aidevopsplatform.service.workflow;

import java.time.Instant;

public record WorkItemSummary(String itemNo, String itemType, String status, String title, String environment,
                               String resourceId, Instant updatedAt) {
}
