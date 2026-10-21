package com.delta.dms.ops.aidevopsplatform.service.port.in;

import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.InspectionReport;
import java.util.List;
import java.util.UUID;
public interface ManagedServiceQueryUseCase {
    List<ManagedService> list(String trustedActorId);
    InspectionReport inspect(String trustedActorId, String serviceId);
    InspectionReport report(String trustedActorId, UUID reportId);
}
