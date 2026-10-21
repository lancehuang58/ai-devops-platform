package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import java.util.List;
import java.util.UUID;

/** Notification facts only; never the execution_outbox or a command retry queue. */
public interface NotificationOutbox {
    List<DomainEvent> pending(int limit);
    void acknowledge(UUID eventId);
}
