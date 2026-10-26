package com.delta.dms.ops.aidevopsplatform.model.alert;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintTest {
    @Test void fpV1UsesEnvironmentResourceSymptomAndSortedDiscriminatorOnly() {
        var disc = new LinkedHashMap<String, String>();
        disc.put("mountpoint", " /var/log ");
        var fingerprint = Fingerprint.of("prod", "vm-jenkins-01", "DISK_USAGE_HIGH", disc);
        assertThat(fingerprint).isEqualTo("c11fda5b84be349f159f19de8ee3f35ed130562cc631f9428d335e5fa032e5a6");
        assertThat(fingerprint).hasSize(64);
    }

    @Test void severityAndSourceDoNotChangeTheFingerprint() {
        var base = Fingerprint.of("PROD", "vm-jenkins-01", "DISK_USAGE_HIGH", Map.of("mountpoint", "/var/log"));
        var again = Fingerprint.of("prod", "vm-jenkins-01", "DISK_USAGE_HIGH", Map.of("mountpoint", "/var/log"));
        assertThat(again).isEqualTo(base);
        assertThat(Fingerprint.of("UAT", "vm-jenkins-01", "DISK_USAGE_HIGH", Map.of("mountpoint", "/var/log"))).isNotEqualTo(base);
        assertThat(Fingerprint.of("PROD", "vm-jenkins-01", "DISK_USAGE_HIGH", Map.of("mountpoint", "/data"))).isNotEqualTo(base);
    }

    @Test void discriminatorKeysAreSortedAndValuesAreTrimmed() {
        var first = Fingerprint.of("PROD", "db-1", "OTHER", Map.of("code", "X", "endpoint", " /health "));
        var second = Fingerprint.of("PROD", "db-1", "OTHER", Map.of("endpoint", "/health", "code", "X"));
        assertThat(first).isEqualTo(second);
        assertThat(Fingerprint.of("PROD", "vm-jenkins-01", "DISK_USAGE_HIGH", Map.of())).isEqualTo(
                "992115095894e9ad183d4a8f948f931d250c503003f6f44738806faac1c84d49");
    }

    @Test void unresolvedResourceIdIsStableAndDoesNotUseSeverity() {
        var id = Fingerprint.unresolvedResourceId("am-prod-01", Map.of("instance", "10.1.2.3:9100", "job", "node"));
        assertThat(id).startsWith("unresolved:am-prod-01:");
        assertThat(id).isEqualTo(Fingerprint.unresolvedResourceId("am-prod-01", Map.of("job", "node", "instance", "10.1.2.3:9100")));
        assertThat(id).isNotEqualTo(Fingerprint.unresolvedResourceId("am-prod-01", Map.of("instance", "10.9.9.9:9100", "job", "node")));
    }
}
