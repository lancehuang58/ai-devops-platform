package com.delta.dms.ops.aidevopsplatform.service.monitoring;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.*;
import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.in.*;
import com.delta.dms.ops.aidevopsplatform.service.port.out.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Fixed, read-only demo use case. No dynamic commands, retries, or transport fallback. */
public final class ManagedServiceQueryService implements ManagedServiceQueryUseCase {
    private final QueryAuthorization authorization;
    private final ManagedServiceRepository services;
    private final ServiceProbePort probe;
    private final ServiceSummaryPort model;
    private final InspectionRepository reports;
    private final DeliverNotificationsUseCase notifications;
    public ManagedServiceQueryService(QueryAuthorization authorization, ManagedServiceRepository services,
            ServiceProbePort probe, ServiceSummaryPort model, InspectionRepository reports,
            DeliverNotificationsUseCase notifications) {
        this.authorization=authorization; this.services=services; this.probe=probe;
        this.model=model; this.reports=reports; this.notifications=notifications;
    }
    @Override public List<ManagedService> list(String actor) {
        authorization.requireAllowed(actor, "*");
        return services.findAll().stream().filter(ManagedService::enabled).toList();
    }
    @Override public InspectionReport inspect(String actor, String serviceId) {
        authorization.requireAllowed(actor, serviceId);
        var service = services.findById(serviceId).filter(ManagedService::enabled)
                .orElseThrow(() -> new NoSuchElementException("Enabled managed service not found"));
        var observation = probe.observe(service);
        var summary = model.summarize(service, observation);
        var report = InspectionReport.complete(UUID.randomUUID(), UUID.randomUUID(), actor, service,
                observation, summary.text(), summary.modelName(), summary.mocked());
        var event = new ServiceInspected(UUID.randomUUID(), report.id(), report.correlationId(), report.observedAt());
        reports.save(report, event);
        notifications.deliverPending(100);
        return report;
    }
    @Override public InspectionReport report(String actor, UUID id) {
        authorization.requireAllowed(actor, "*");
        return reports.findById(id).orElseThrow(() -> new NoSuchElementException("Report not found"));
    }
}
