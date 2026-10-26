package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;

/** Fake or gateway-backed execution. v1 does not call a real VM or MCP endpoint. */
public interface ChangeExecutionPort {
    boolean apply(WorkItemSnapshot item);
    boolean verify(WorkItemSnapshot item);
}
