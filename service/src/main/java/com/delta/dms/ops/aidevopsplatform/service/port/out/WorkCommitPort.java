package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.work.WorkSnapshot;
import java.util.List;

public interface WorkCommitPort {
    /** Atomically create work and its notification outbox events, or write neither.
     * Duplicate work/event IDs must fail; publishing is not part of this transaction.
     */
    void create(WorkSnapshot work, List<DomainEvent> events);
}
