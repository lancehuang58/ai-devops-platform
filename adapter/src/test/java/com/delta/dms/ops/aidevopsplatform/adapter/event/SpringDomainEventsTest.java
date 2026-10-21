package com.delta.dms.ops.aidevopsplatform.adapter.event;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.work.WorkReceived;
import com.delta.dms.ops.aidevopsplatform.service.port.in.DomainEventHandler;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SpringDomainEventsTest {
    private DomainEvent event() { return new WorkReceived(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()); }
    @Test void publisherPreservesEventIdentity() {
        var seen = new ArrayList<Object>(); var event = event();
        new SpringDomainEventPublisher(seen::add).publish(event);
        assertThat(seen).containsExactly(event);
    }
    @Test void listenerDoesNotSwallowAConsumerFailure() {
        DomainEventHandler handler = new DomainEventHandler() {
            public boolean supports(DomainEvent e) { return true; }
            public void handle(DomainEvent e) { throw new IllegalStateException("Projection failed"); }
        };
        assertThatThrownBy(() -> new SpringDomainEventListener(List.of(handler)).on(event()))
                .hasMessage("Projection failed");
    }
    @Test void unhandledEventsAreRejectedWithoutInvokingAnUnrelatedHandler() {
        DomainEventHandler handler = new DomainEventHandler() {
            public boolean supports(DomainEvent e) { return false; }
            public void handle(DomainEvent e) { fail("Wrong event routed"); }
        };
        assertThatThrownBy(() -> new SpringDomainEventListener(List.of(handler)).on(event()))
                .hasMessage("No handler registered for domain event");
    }
}
