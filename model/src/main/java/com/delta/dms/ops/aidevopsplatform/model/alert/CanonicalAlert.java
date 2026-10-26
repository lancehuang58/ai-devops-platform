package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CanonicalAlert(
        String schemaVersion,
        UUID eventId,
        SourceType sourceType,
        String sourceId,
        String normalizer,
        String sourceEventId,
        String sourceFingerprint,
        String fingerprint,
        String fingerprintVersion,
        String resourceId,
        String resourceType,
        boolean resourceResolved,
        String environment,
        List<String> serviceRefs,
        String symptomType,
        String symptomCode,
        Map<String, String> discriminator,
        Severity severity,
        SignalStatus status,
        Instant observedAt,
        Instant startsAt,
        Instant endsAt,
        Instant receivedAt,
        String summary,
        UUID receiptId) {
}
