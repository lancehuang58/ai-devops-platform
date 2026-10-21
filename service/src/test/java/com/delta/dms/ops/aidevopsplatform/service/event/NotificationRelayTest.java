package com.delta.dms.ops.aidevopsplatform.service.event;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.work.WorkReceived;
import com.delta.dms.ops.aidevopsplatform.service.port.out.NotificationOutbox;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class NotificationRelayTest {
    private DomainEvent event() { return new WorkReceived(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()); }
    @Test void failedDeliveryRemainsPendingAndStopsTheBatch() {
        var events = List.of(event(), event());
        var acknowledgements = new ArrayList<UUID>();
        NotificationOutbox outbox = new NotificationOutbox() {
            public List<DomainEvent> pending(int limit) { return events; }
            public void acknowledge(UUID id) { acknowledgements.add(id); }
        };
        var attempted = new ArrayList<UUID>();
        var relay = new NotificationRelay(outbox, event -> {
            attempted.add(event.eventId()); throw new IllegalStateException("Consumer failed");
        });
        assertThatThrownBy(() -> relay.deliverPending(10)).hasMessage("Consumer failed");
        assertThat(attempted).containsExactly(events.getFirst().eventId());
        assertThat(acknowledgements).isEmpty();
    }
    @Test void acknowledgementOccursOnlyAfterPublication() {
        var calls = new ArrayList<String>(); var event = event();
        NotificationOutbox outbox = new NotificationOutbox() {
            public List<DomainEvent> pending(int limit) { return List.of(event); }
            public void acknowledge(UUID id) { calls.add("ack"); }
        };
        assertThat(new NotificationRelay(outbox, e -> calls.add("publish")).deliverPending(1)).isEqualTo(1);
        assertThat(calls).containsExactly("publish", "ack");
    }
}
