package com.delta.dms.ops.aidevopsplatform.service.workflow;

import java.time.Instant;

public record WorkItemListQuery(String system, Instant updatedFrom, Instant updatedTo, String status,
                                 boolean openOnly, int page, int size) {
}
