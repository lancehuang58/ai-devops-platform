package com.delta.dms.ops.aidevopsplatform.model.catalog;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record MonitoredService(
        String id,
        String type,
        String environment,
        String endpointUrl,
        String apiUrl,
        String mcpServerId,
        String gloveHost,
        String credentialRef,
        List<ProbeChannel> probeOverride,
        Duration intervalOverride,
        Map<String, String> thresholdOverrides) {
}
