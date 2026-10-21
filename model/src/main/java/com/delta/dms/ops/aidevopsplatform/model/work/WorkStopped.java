package com.delta.dms.ops.aidevopsplatform.model.work;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkStopped(UUID eventId, UUID aggregateId, UUID correlationId,
                          Instant occurredAt, StopReason reason) implements DomainEvent {
    public WorkStopped {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(reason);
    }
}
