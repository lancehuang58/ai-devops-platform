package com.delta.dms.ops.aidevopsplatform.model.alert;

import com.delta.dms.ops.aidevopsplatform.model.security.SecretFilter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AlertNormalizer {
    private static final Instant ZERO_ENDS_AT = Instant.parse("1970-01-02T00:00:00Z");

    public void requireAlertmanagerVersion(String version) {
        if (!"4".equals(version)) throw new InvalidAlertException("unsupported Alertmanager payload version");
    }

    public CanonicalAlert alertmanager(String sourceId, AlertmanagerAlert alert, SymptomMapping mapping,
                                       ResourceIdentity resource, Instant receivedAt, UUID eventId, UUID receiptId, String raw) {
        var status = SignalStatus.parse(alert.status());
        var labels = alert.labels() == null ? Map.<String, String>of() : alert.labels();
        var code = labels.getOrDefault("alertname", "unknown");
        var symptomType = mapping == null ? "OTHER" : mapping.symptomType();
        var discriminator = new LinkedHashMap<String, String>();
        if (mapping == null) discriminator.put("code", code);
        else for (var key : mapping.discriminatorKeys()) {
            if (labels.containsKey(key)) discriminator.put(key, labels.get(key));
        }
        if ("OTHER".equals(symptomType) && !discriminator.containsKey("code")) discriminator.put("code", code);
        var endsAt = alert.endsAt() == null || alert.endsAt().isBefore(ZERO_ENDS_AT) ? null : alert.endsAt();
        var observedAt = status == SignalStatus.FIRING || endsAt == null ? alert.startsAt() : endsAt;
        var summary = SecretFilter.redact(text(alert.annotations(), "summary"));
        var fingerprint = Fingerprint.of(resource.environment(), resource.resourceId(), symptomType, discriminator);
        var sourceEventId = alert.sourceFingerprint() + ":" + alert.startsAt().toEpochMilli() + ":" + alert.status().trim().toLowerCase(java.util.Locale.ROOT);
        return new CanonicalAlert("1.0", eventId, SourceType.PROMETHEUS_ALERTMANAGER, sourceId, "alertmanager-v4@1",
                sourceEventId, alert.sourceFingerprint(), fingerprint, Fingerprint.VERSION, resource.resourceId(),
                resource.resourceType(), resource.resolved(), resource.environment(), List.copyOf(resource.serviceRefs()),
                symptomType, code, Map.copyOf(discriminator), Severity.parse(labels.get("severity")), status,
                observedAt, alert.startsAt(), endsAt, receivedAt, summary.isBlank() ? code : summary, receiptId);
    }

    public CanonicalAlert generic(GenericAlert alert, Instant receivedAt, UUID eventId, UUID receiptId, String raw) {
        var status = SignalStatus.parse(alert.status());
        var discriminator = new LinkedHashMap<String, String>();
        if (alert.discriminator() != null) discriminator.putAll(alert.discriminator());
        if ("OTHER".equals(alert.symptomType())) discriminator.putIfAbsent("code", alert.symptomCode());
        var fingerprint = Fingerprint.of(alert.environment(), alert.resourceId(), alert.symptomType(), discriminator);
        var sourceEventId = alert.checkId() + ":" + alert.observedAt() + ":" + status.name();
        var sourceType = alert.sourceId() != null && alert.sourceId().contains("probe")
                ? SourceType.HTTP_ENDPOINT_PROBE : SourceType.CUSTOM_CONNECTOR;
        return new CanonicalAlert("1.0", eventId, sourceType, alert.sourceId(), "generic-api@1", sourceEventId, null,
                fingerprint, Fingerprint.VERSION, alert.resourceId(), alert.resourceType(), true, alert.environment(),
                List.of(), alert.symptomType(), alert.symptomCode(), Map.copyOf(discriminator),
                Severity.parse(alert.severity()), status, alert.observedAt(), alert.observedAt(), null, receivedAt,
                SecretFilter.redact(alert.summary()), receiptId);
    }

    private static String text(Map<String, String> annotations, String key) {
        if (annotations == null || annotations.get(key) == null) return "";
        return annotations.get(key);
    }
}
