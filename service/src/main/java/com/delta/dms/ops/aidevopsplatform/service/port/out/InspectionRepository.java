package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.InspectionReport;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ServiceInspected;
import java.util.Optional;
import java.util.UUID;
public interface InspectionRepository {
    /** Save report and event atomically; a failure saves neither. */
    void save(InspectionReport report, ServiceInspected event);
    Optional<InspectionReport> findById(UUID id);
}
