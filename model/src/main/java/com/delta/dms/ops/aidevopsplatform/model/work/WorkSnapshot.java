package com.delta.dms.ops.aidevopsplatform.model.work;

import java.util.Objects;
import java.util.UUID;

/** Immutable persistence boundary; no question, secret, or raw evidence payload. */
public record WorkSnapshot(UUID id, UUID correlationId, String actorId,
                           String resourceId, RequestStatus status) {
    public WorkSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(status);
        if (actorId == null || actorId.isBlank() || resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("Actor and resource references are required");
        }
    }
}
