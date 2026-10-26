package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.time.Instant;
import java.util.Map;

public record GenericAlert(
        String sourceId,
        String checkId,
        String resourceId,
        String resourceType,
        String environment,
        String symptomType,
        String symptomCode,
        Map<String, String> discriminator,
        String severity,
        String status,
        Instant observedAt,
        String summary) {
}
