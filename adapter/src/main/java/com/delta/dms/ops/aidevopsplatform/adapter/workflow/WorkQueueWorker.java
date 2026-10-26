package com.delta.dms.ops.aidevopsplatform.adapter.workflow;

import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChangeExecutionPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.LlmPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ResourceLockPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemStore;
import com.delta.dms.ops.aidevopsplatform.service.workflow.CollectedEvidence;
import com.delta.dms.ops.aidevopsplatform.service.workflow.FixCatalog;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemCoordinator;
import com.delta.dms.ops.aidevopsplatform.model.remediation.MatchContext;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Claims up to {@code workers} ready tasks. Waiting states are persisted and the task is released. */
public final class WorkQueueWorker {
    private final JdbcPlatformStore queue;
    private final WorkItemStore items;
    private final WorkItemCoordinator coordinator;
    private final LlmPort llm;
    private final ChangeExecutionPort execution;
    private final ResourceLockPort locks;
    private final FixCatalog catalog;
    private final int workers;

    public WorkQueueWorker(JdbcPlatformStore queue, WorkItemStore items, WorkItemCoordinator coordinator, LlmPort llm,
                            ChangeExecutionPort execution, ResourceLockPort locks, FixCatalog catalog, int workers) {
        this.queue = queue;
        this.items = items;
        this.coordinator = coordinator;
        this.llm = llm;
        this.execution = execution;
        this.locks = locks;
        this.catalog = catalog;
        this.workers = workers;
    }

    public int tick(Instant now) {
        int progressed = 0;
        for (int i = 0; i < workers; i++) {
            var claim = queue.claimNext("worker-" + i, now, Duration.ofMinutes(5));
            if (claim.isEmpty()) break;
            progress(claim.get().itemId(), now);
            queue.completeTask(claim.get().taskId());
            progressed++;
        }
        return progressed;
    }

    private void progress(UUID itemId, Instant now) {
        var tracked = items.findByItemId(itemId).orElseThrow(() -> new IllegalStateException("queued item missing"));
        var item = tracked.item();
        for (int step = 0; step < 8; step++) {
            var before = item.snapshot().status();
            var version = item.snapshot().rowVersion();
            coordinator.advance(item, match(item.snapshot().environment(), queue.primaryResource(itemId)),
                    CollectedEvidence.ready(), catalog, llm, execution, locks, now, UUID.randomUUID());
            if (item.snapshot().status() == before && item.snapshot().rowVersion() == version) break;
            if (item.snapshot().status() == before) break;
        }
        items.save(tracked);
    }

    private static MatchContext match(String environment, String resourceId) {
        return new MatchContext("DISK_USAGE_HIGH", environment, resourceId == null ? "" : resourceId, "", false);
    }
}
