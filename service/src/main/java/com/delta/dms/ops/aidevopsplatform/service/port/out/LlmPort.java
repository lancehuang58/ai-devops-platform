package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;

public interface LlmPort {
    boolean available();
    String draftProposal(WorkItemSnapshot item);
}
