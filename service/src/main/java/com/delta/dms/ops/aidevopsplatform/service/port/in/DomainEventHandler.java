package com.delta.dms.ops.aidevopsplatform.service.port.in;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;

/** Consumers must tolerate delivery more than once, using eventId. */
public interface DomainEventHandler {
    boolean supports(DomainEvent event);
    void handle(DomainEvent event);
}
