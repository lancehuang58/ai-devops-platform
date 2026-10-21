package com.delta.dms.ops.aidevopsplatform.service.event;

import com.delta.dms.ops.aidevopsplatform.service.port.out.DomainEventPublisher;
import com.delta.dms.ops.aidevopsplatform.service.port.out.NotificationOutbox;
import java.util.Objects;

/** Single-worker notification delivery. No scheduler or automatic retry is installed. */
public final class NotificationRelay {
    private final NotificationOutbox outbox;
    private final DomainEventPublisher publisher;
    public NotificationRelay(NotificationOutbox outbox, DomainEventPublisher publisher) {
        this.outbox = Objects.requireNonNull(outbox);
        this.publisher = Objects.requireNonNull(publisher);
    }
    public int deliverPending(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid batch size");
        int delivered = 0;
        for (var event : outbox.pending(limit)) {
            publisher.publish(event);
            outbox.acknowledge(event.eventId());
            delivered++;
        }
        return delivered;
    }
}
