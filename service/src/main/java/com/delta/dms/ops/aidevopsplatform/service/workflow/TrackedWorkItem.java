package com.delta.dms.ops.aidevopsplatform.service.workflow;

import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItem;

public record TrackedWorkItem(WorkItem item, long loadedVersion) {
    public static TrackedWorkItem fresh(WorkItem item) {
        return new TrackedWorkItem(item, -1);
    }
}
