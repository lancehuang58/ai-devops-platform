package com.delta.dms.ops.aidevopsplatform.model.alert;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertNormalizerTest {
    private static final Instant START = Instant.parse("2026-10-26T07:30:00Z");
    private static final Instant RECEIVED = Instant.parse("2026-10-26T07:30:04Z");
    private static final UUID EVENT = UUID.fromString("5f0c3a2e-8a7b-4c1d-9e55-2b7f0d1a9c41");
    private static final UUID RECEIPT = UUID.fromString("b7e2d0c4-1f3a-4e8b-9c6d-0a1b2c3d4e5f");

    private final AlertNormalizer normalizer = new AlertNormalizer();

    @Test void alertmanagerFiringBecomesCanonicalAndKeepsSourceOutOfFingerprint() {
        var alert = new AlertmanagerAlert("firing",
                Map.of("alertname", "NodeFilesystemAlmostFull", "severity", "major", "env", "prod",
                        "instance", "10.1.2.3:9100", "mountpoint", "/var/log", "password", "s3cret"),
                Map.of("summary", "disk password=s3cret", "description", "low space"),
                START, Instant.parse("0001-01-01T00:00:00Z"),
                "http://prometheus.example/graph", "a1b2c3d4e5f60718");
        var mapping = new SymptomMapping("DISK_USAGE_HIGH", java.util.List.of("mountpoint"));
        var resource = new ResourceIdentity("vm-jenkins-01", "VM", "PROD", true, java.util.List.of("svc-jenkins"));
        var event = normalizer.alertmanager("am-prod-01", alert, mapping, resource, RECEIVED, EVENT, RECEIPT, "payload");

        assertThat(event.schemaVersion()).isEqualTo("1.0");
        assertThat(event.fingerprintVersion()).isEqualTo("fp-v1");
        assertThat(event.sourceEventId()).isEqualTo("a1b2c3d4e5f60718:" + START.toEpochMilli() + ":firing");
        assertThat(event.severity()).isEqualTo(Severity.MAJOR);
        assertThat(event.status()).isEqualTo(SignalStatus.FIRING);
        assertThat(event.endsAt()).isNull();
        assertThat(event.observedAt()).isEqualTo(START);
        assertThat(event.symptomType()).isEqualTo("DISK_USAGE_HIGH");
        assertThat(event.discriminator()).containsExactly(Map.entry("mountpoint", "/var/log"));
        assertThat(event.summary()).doesNotContain("s3cret").contains("[REDACTED");
        assertThat(event.fingerprint()).isEqualTo(Fingerprint.of("PROD", "vm-jenkins-01", "DISK_USAGE_HIGH", event.discriminator()));
    }

    @Test void sameProblemFromAnotherSourceSharesTheFingerprint() {
        var mapping = new SymptomMapping("DISK_USAGE_HIGH", java.util.List.of("mountpoint"));
        var resource = new ResourceIdentity("vm-jenkins-01", "VM", "PROD", true, java.util.List.of());
        var fromPrometheus = normalizer.alertmanager("am-prod-01", firing("major"), mapping, resource, RECEIVED, EVENT, RECEIPT, "a");
        var fromProbe = normalizer.generic(new GenericAlert("http-probe-01", "check-1", "vm-jenkins-01", "VM",
                "PROD", "DISK_USAGE_HIGH", "disk-check", Map.of("mountpoint", "/var/log"), "CRITICAL", "FIRING",
                START, "/var/log full"), RECEIVED, UUID.randomUUID(), UUID.randomUUID(), "b");
        assertThat(fromProbe.fingerprint()).isEqualTo(fromPrometheus.fingerprint());
        assertThat(fromProbe.sourceType()).isEqualTo(SourceType.HTTP_ENDPOINT_PROBE);
        assertThat(fromProbe.sourceEventId()).isEqualTo("check-1:" + START + ":FIRING");
    }

    @Test void unknownAlertNameBecomesOtherAndIncludesTheCode() {
        var alert = firing("warning");
        var event = normalizer.alertmanager("am-prod-01", alert, null,
                new ResourceIdentity("vm-jenkins-01", "VM", "PROD", true, java.util.List.of()),
                RECEIVED, EVENT, RECEIPT, "raw");
        assertThat(event.symptomType()).isEqualTo("OTHER");
        assertThat(event.discriminator()).containsEntry("code", "NodeFilesystemAlmostFull");
    }

    @Test void rejectedWebhookVersionIsInvalid() {
        assertThatThrownBy(() -> normalizer.requireAlertmanagerVersion("3"))
                .isInstanceOf(InvalidAlertException.class);
    }

    private static AlertmanagerAlert firing(String severity) {
        return new AlertmanagerAlert("firing",
                Map.of("alertname", "NodeFilesystemAlmostFull", "severity", severity, "mountpoint", "/var/log"),
                Map.of("summary", "/var/log 93%"), START, null, "http://prometheus.example/graph", "a1b2c3d4e5f60718");
    }
}
