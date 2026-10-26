package com.delta.dms.ops.aidevopsplatform.adapter.execution;

import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChangeExecutionPort;

/** Fake MCP/VM execution. v1 does not open a shell or call a gateway. */
public final class RejectingExecution implements ChangeExecutionPort {
    @Override public boolean apply(WorkItemSnapshot item) { return false; }
    @Override public boolean verify(WorkItemSnapshot item) { return false; }
}
