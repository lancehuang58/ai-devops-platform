package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import java.util.List;

public record ProbeTarget(
        String instanceId,
        String serviceType,
        String environment,
        List<ProbeChannel> channels,
        String endpointUrl,
        String apiUrl,
        String mcpServerId,
        String gloveHost) {
}
