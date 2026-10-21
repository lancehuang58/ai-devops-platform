package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The observed status is authoritative; a model may explain, never replace it. */
public record InspectionReport(UUID id, UUID correlationId, String actorId, String serviceId,
        String serviceName, Observation.Status status, String version, Instant observedAt,
        String summary, String modelName, boolean probeMocked, boolean modelMocked) {
    public InspectionReport {
        Objects.requireNonNull(id); Objects.requireNonNull(correlationId); Objects.requireNonNull(status);
        Objects.requireNonNull(observedAt);
        for (var value : new String[]{actorId, serviceId, serviceName, version, summary, modelName})
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Report fields required");
        if (summary.length() > 4000) throw new IllegalArgumentException("Summary too large");
    }
    public static InspectionReport complete(UUID id, UUID correlationId, String actorId,
            ManagedService service, Observation observation, String summary, String modelName, boolean modelMocked) {
        if (!service.enabled()) throw new IllegalStateException("Service is disabled");
        return new InspectionReport(id, correlationId, actorId, service.id(), service.name(), observation.status(),
                observation.version(), observation.observedAt(), summary, modelName, observation.mocked(), modelMocked);
    }
}
