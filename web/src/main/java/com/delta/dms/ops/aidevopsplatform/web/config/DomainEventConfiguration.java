package com.delta.dms.ops.aidevopsplatform.web.config;

import com.delta.dms.ops.aidevopsplatform.adapter.event.SpringDomainEventListener;
import com.delta.dms.ops.aidevopsplatform.adapter.event.SpringDomainEventPublisher;
import com.delta.dms.ops.aidevopsplatform.service.port.in.DomainEventHandler;
import com.delta.dms.ops.aidevopsplatform.service.port.out.DomainEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DomainEventConfiguration {
    @Bean DomainEventPublisher domainEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringDomainEventPublisher(publisher);
    }
    @Bean SpringDomainEventListener domainEventListener(ObjectProvider<DomainEventHandler> handlers) {
        return new SpringDomainEventListener(handlers.orderedStream().toList());
    }
}
