package com.delta.dms.ops.aidevopsplatform.model.event;

import java.time.Instant;
import java.util.UUID;

/** An immutable fact. It is never an authorization or a command to execute. */
public interface DomainEvent {
    UUID eventId();
    UUID aggregateId();
    UUID correlationId();
    Instant occurredAt();
}
