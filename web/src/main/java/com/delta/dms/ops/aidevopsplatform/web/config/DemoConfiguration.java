package com.delta.dms.ops.aidevopsplatform.web.config;

import com.delta.dms.ops.aidevopsplatform.adapter.config.YamlManagedServiceRepository;
import com.delta.dms.ops.aidevopsplatform.adapter.access.ConfiguredRoles;
import com.delta.dms.ops.aidevopsplatform.adapter.mock.*;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.H2DemoInspectionStore;
import com.delta.dms.ops.aidevopsplatform.adapter.event.InspectionReceiptHandler;
import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.in.*;
import com.delta.dms.ops.aidevopsplatform.service.port.out.*;
import com.delta.dms.ops.aidevopsplatform.service.event.NotificationRelay;
import com.delta.dms.ops.aidevopsplatform.service.monitoring.ManagedServiceQueryService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@Profile("demo")
@EnableConfigurationProperties(PlatformSettings.class)
public class DemoConfiguration {
    public DemoConfiguration(PlatformSettings settings) {
        if(settings.database()==null || settings.ldap()==null || settings.llm()==null || settings.catalog()==null
                || !"h2-mock".equals(settings.database().mode()) || !"mock".equals(settings.ldap().mode())
                || !"mock".equals(settings.llm().mode()) || !"yaml".equals(settings.catalog().mode()))
            throw new IllegalArgumentException("Demo requires h2-mock DB, mock LDAP/LLM and YAML catalog; real adapters are not implemented");
    }
    @Bean ManagedServiceRepository managedServices(PlatformSettings settings) {
        var repository=new YamlManagedServiceRepository(Path.of(settings.catalog().file()));
        for(var service:repository.findAll())
            if(service.connection().transport()!=ManagedService.Transport.MOCK || !"TEST".equals(service.environment())
                    || !"jenkins-basic-v1".equals(service.healthProfile()))
                throw new IllegalArgumentException("Demo catalog accepts TEST/MOCK/jenkins-basic-v1 resources only");
        return repository;
    }
    @Bean ConfiguredRoles configuredRoles(PlatformSettings settings) { return new ConfiguredRoles(Path.of(settings.rolesFile())); }
    @Bean DirectoryAuthenticationPort directoryAuthentication(PlatformSettings settings) { return new MockLdapDirectory(settings.ldap().users()); }
    @Bean ServiceProbePort serviceProbe() { return new MockJenkinsProbe(Clock.systemUTC()); }
    @Bean ServiceSummaryPort serviceSummary(PlatformSettings settings) { return new MockLlmSummaryAdapter(settings.llm().modelName()); }
    @Bean H2DemoInspectionStore inspectionStore(PlatformSettings settings) {
        var db=settings.database(); return new H2DemoInspectionStore(db.jdbcUrl(),db.username(),db.password());
    }
    @Bean DomainEventHandler inspectionReceipt(EventReceiptPort receipts) { return new InspectionReceiptHandler(receipts); }
    @Bean DeliverNotificationsUseCase deliverNotifications(NotificationOutbox outbox, DomainEventPublisher publisher) {
        return new NotificationRelay(outbox,publisher);
    }
    @Bean ManagedServiceQueryUseCase managedServiceQuery(QueryAuthorization authorization, ManagedServiceRepository services,
            ServiceProbePort probe, ServiceSummaryPort model, InspectionRepository reports, DeliverNotificationsUseCase notifications) {
        return new ManagedServiceQueryService(authorization,services,probe,model,reports,notifications);
    }
}
