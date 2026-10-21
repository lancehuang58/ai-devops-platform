package com.delta.dms.ops.aidevopsplatform.model.work;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkReceived(UUID eventId, UUID aggregateId, UUID correlationId,
                          Instant occurredAt) implements DomainEvent {
    public WorkReceived {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(occurredAt);
    }
}
