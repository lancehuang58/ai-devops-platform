package com.delta.dms.ops.aidevopsplatform.service.workflow;

import java.util.List;

public record WorkItemPage(List<WorkItemSummary> items, int page, int size, int totalPages, long totalElements) {
}
