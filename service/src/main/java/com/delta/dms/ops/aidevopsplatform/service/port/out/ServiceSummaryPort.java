package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.Observation;
public interface ServiceSummaryPort {
    Summary summarize(ManagedService service, Observation observation);
    record Summary(String text, String modelName, boolean mocked) {}
}
