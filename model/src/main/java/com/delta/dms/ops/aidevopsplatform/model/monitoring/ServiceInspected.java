package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ServiceInspected(UUID eventId, UUID aggregateId, UUID correlationId, Instant occurredAt)
        implements DomainEvent {
    public ServiceInspected {
        Objects.requireNonNull(eventId); Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(correlationId); Objects.requireNonNull(occurredAt);
    }
}
