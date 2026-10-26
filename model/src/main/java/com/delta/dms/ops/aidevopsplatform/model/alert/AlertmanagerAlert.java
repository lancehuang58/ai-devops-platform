package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.time.Instant;
import java.util.Map;

public record AlertmanagerAlert(
        String status,
        Map<String, String> labels,
        Map<String, String> annotations,
        Instant startsAt,
        Instant endsAt,
        String generatorUrl,
        String sourceFingerprint) {
}
