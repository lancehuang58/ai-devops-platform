package com.delta.dms.ops.aidevopsplatform.model.catalog;

import java.util.List;

public record ServiceCatalog(
        List<ServiceTemplate> templates,
        List<MonitoredService> monitored,
        List<IntegratedService> integrated) {
}
