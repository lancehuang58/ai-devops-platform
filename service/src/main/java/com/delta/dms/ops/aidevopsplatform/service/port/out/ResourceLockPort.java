package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;

public interface ResourceLockPort {
    boolean tryAcquire(WorkItemSnapshot item);
}
