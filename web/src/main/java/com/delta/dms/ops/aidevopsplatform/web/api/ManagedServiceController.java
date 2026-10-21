package com.delta.dms.ops.aidevopsplatform.web.api;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.InspectionReport;
import com.delta.dms.ops.aidevopsplatform.service.port.in.ManagedServiceQueryUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

@RestController
@Profile("demo")
@RequestMapping("/api/v1")
public class ManagedServiceController {
    private final ManagedServiceQueryUseCase queries;
    public ManagedServiceController(ManagedServiceQueryUseCase queries) { this.queries=queries; }
    public record ServiceView(String id,String name,String kind,String environment,String transport) {}
    public record ReportView(UUID reportId,UUID correlationId,String serviceId,String serviceName,String status,
            String version,Instant observedAt,String summary,String modelName,boolean probeMocked,boolean modelMocked) {}
    @GetMapping("/managed-services")
    public ResponseEntity<List<ServiceView>> list(Principal principal) {
        var result=queries.list(principal.getName()).stream().map(s -> new ServiceView(s.id(),s.name(),s.kind().name(),
                s.environment(),s.connection().transport().name())).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping("/managed-services/{id}/status")
    public ResponseEntity<ReportView> inspect(Principal principal,@PathVariable String id) {
        return response(queries.inspect(principal.getName(),id));
    }
    @GetMapping("/reports/{id}")
    public ResponseEntity<ReportView> report(Principal principal,@PathVariable UUID id) {
        return response(queries.report(principal.getName(),id));
    }
    private ResponseEntity<ReportView> response(InspectionReport r) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ReportView(r.id(),r.correlationId(),
                r.serviceId(),r.serviceName(),r.status().name(),r.version(),r.observedAt(),r.summary(),r.modelName(),
                r.probeMocked(),r.modelMocked()));
    }
}
