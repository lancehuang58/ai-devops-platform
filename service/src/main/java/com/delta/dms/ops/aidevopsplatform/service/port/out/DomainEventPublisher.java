package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;

public interface DomainEventPublisher {
    /** Return after synchronous consumers succeed; propagate failures. */
    void publish(DomainEvent event);
}
