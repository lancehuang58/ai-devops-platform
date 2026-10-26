package com.delta.dms.ops.aidevopsplatform.adapter.alert;

import com.delta.dms.ops.aidevopsplatform.model.alert.ResourceIdentity;
import com.delta.dms.ops.aidevopsplatform.model.alert.SymptomMapping;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** v1 label mapping. Unknown alert names stay OTHER so the normalizer keeps the code. */
public final class AlertFieldMapper {
    public ResourceIdentity resource(Map<String, String> labels) {
        var env = labels.getOrDefault("env", labels.getOrDefault("environment", "PROD"));
        var resourceId = labels.getOrDefault("resource_id", labels.getOrDefault("instance", labels.getOrDefault("resource", "unresolved")));
        var colon = resourceId.indexOf(':');
        if (colon > 0 && !labels.containsKey("resource_id")) resourceId = resourceId.substring(0, colon);
        var services = labels.containsKey("service") ? List.of(labels.get("service")) : List.<String>of();
        return new ResourceIdentity(resourceId, labels.getOrDefault("resource_type", "VM"), env.toUpperCase(Locale.ROOT), true, services);
    }

    public SymptomMapping symptom(String alertname) {
        var name = alertname == null ? "" : alertname.toLowerCase(Locale.ROOT);
        if (name.contains("disk")) return new SymptomMapping("DISK_USAGE_HIGH", List.of("mountpoint"));
        if (name.contains("health")) return new SymptomMapping("HEALTH_CHECK_FAILED", List.of());
        if (name.contains("storage")) return new SymptomMapping("STORAGE_UNRESPONSIVE", List.of());
        if (name.contains("down") || name.contains("unavailable")) return new SymptomMapping("SERVICE_UNAVAILABLE", List.of());
        return null;
    }
}
