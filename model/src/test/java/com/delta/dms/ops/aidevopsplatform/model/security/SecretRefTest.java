package com.delta.dms.ops.aidevopsplatform.model.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretRefTest {
    @Test void environmentReferenceResolvesAndNeverPrintsTheValue() {
        var ref = SecretRef.parse("${HARBOR_TOKEN}");
        assertThat(ref.name()).isEqualTo("HARBOR_TOKEN");
        assertThat(ref.toString()).isEqualTo("${HARBOR_TOKEN}");
        assertThat(ref.resolve(name -> "super-secret-value")).isEqualTo("super-secret-value");
        assertThat(ref.toString()).doesNotContain("super-secret-value");
    }

    @Test void literalSecretsAndMalformedReferencesAreRejected() {
        assertThatThrownBy(() -> SecretRef.parse("hunter2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretRef.parse("Bearer abc.def")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretRef.parse("${lowercase}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretRef.parse("${HARBOR_TOKEN} extra")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretRef.parse(" ${HARBOR_TOKEN}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretRef.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretRef.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingOrBlankEnvironmentFailsAndNamesTheReference() {
        var ref = SecretRef.parse("${HARBOR_TOKEN}");
        assertThatThrownBy(() -> ref.resolve(name -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("${HARBOR_TOKEN}")
                .hasMessageNotContaining("super-secret-value");
        assertThatThrownBy(() -> ref.resolve(name -> "  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
