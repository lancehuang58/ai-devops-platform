package com.delta.dms.ops.aidevopsplatform.model.catalog;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ServiceTemplate(
        String id,
        String type,
        List<ProbeChannel> probes,
        Duration interval,
        Map<String, String> thresholds) {
}
