package com.delta.dms.ops.aidevopsplatform.adapter.workflow;

import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ResourceLockPort;

import java.time.Duration;
import java.time.Instant;

public final class JdbcResourceLock implements ResourceLockPort {
    private final JdbcPlatformStore store;

    public JdbcResourceLock(JdbcPlatformStore store) { this.store = store; }

    @Override
    public boolean tryAcquire(WorkItemSnapshot item) {
        var resource = store.primaryResource(item.itemId());
        if (resource == null || resource.isBlank()) return true;
        return store.tryAcquire(item.itemId(), resource, "RUNNING", "worker", 1, Instant.now(), Duration.ofMinutes(30));
    }
}
