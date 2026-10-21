package com.delta.dms.ops.aidevopsplatform.adapter.event;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.service.port.out.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Objects;

public final class SpringDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher publisher;
    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher);
    }
    @Override public void publish(DomainEvent event) {
        publisher.publishEvent(Objects.requireNonNull(event));
    }
}
