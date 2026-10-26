package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.service.workflow.TrackedWorkItem;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemStore {
    Optional<TrackedWorkItem> findByItemNo(String itemNo);
    Optional<TrackedWorkItem> findByItemId(UUID itemId);
    void save(TrackedWorkItem tracked);
    void recordTakeover(UUID takeoverId, UUID itemId, String operator, String tool, String model, String reason, String fromStatus, Instant now);
    void recordWriteback(UUID takeoverId, String summary, String rootCause, String actions, String evidence, String operator, Instant now);
    void recordPackage(UUID takeoverId, byte[] body, String sha256, String operator, Instant now);
    boolean hasWriteback(UUID itemId);
    boolean firing(UUID itemId);
}
