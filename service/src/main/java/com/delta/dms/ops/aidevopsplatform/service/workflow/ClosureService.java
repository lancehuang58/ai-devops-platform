package com.delta.dms.ops.aidevopsplatform.service.workflow;

import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemStore;

import java.time.Instant;
import java.util.UUID;

public final class ClosureService {
    private final WorkItemStore store;

    public ClosureService(WorkItemStore store) { this.store = store; }

    public void confirm(String itemNo, ClosureReason reason, UUID duplicateOf, Instant now, UUID eventId) {
        var tracked = store.findByItemNo(itemNo).orElseThrow(() -> new IllegalArgumentException("work item not found"));
        tracked.item().confirmClosure(reason, duplicateOf, now, eventId);
        store.save(tracked);
    }
}
