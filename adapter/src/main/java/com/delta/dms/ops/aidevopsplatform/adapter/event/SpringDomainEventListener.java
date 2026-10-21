package com.delta.dms.ops.aidevopsplatform.adapter.event;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.service.port.in.DomainEventHandler;
import org.springframework.context.event.EventListener;
import java.util.List;

public final class SpringDomainEventListener {
    private final List<DomainEventHandler> handlers;
    public SpringDomainEventListener(List<DomainEventHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }
    @EventListener
    public void on(DomainEvent event) {
        boolean handled = false;
        for (var handler : handlers) {
            if (handler.supports(event)) {
                handler.handle(event);
                handled = true;
            }
        }
        if (!handled) throw new IllegalStateException("No handler registered for domain event");
    }
}
