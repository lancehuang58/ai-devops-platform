package com.delta.dms.ops.aidevopsplatform.model.takeover;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExportMaskerTest {
    private final ExportMasker masker = new ExportMasker();

    @Test void secretsHostsAndPeopleAreMaskedConsistently() {
        var text = """
                password=s3cret token: abc.def.ghi
                jdbc:postgresql://db.internal:5432/ops?user=ops&password=s3cret
                https://ada:s3cret@gitlab.internal/repo
                contact ada@corp.example about 10.1.2.3 and db.internal
                Authorization: Bearer eyJhbGciOiJub25lIn0.payload.sig
                """;
        var masked = masker.mask(text, "ada");
        assertThat(masked.text()).doesNotContain("s3cret", "eyJhbGciOiJub25lIn0", "ada@corp.example", "10.1.2.3", "db.internal");
        assertThat(masked.text()).contains("[REDACTED:PASSWORD]", "[REDACTED:TOKEN]", "[REDACTED:AUTHORIZATION]");
        assertThat(masked.text()).contains("ada");
        assertThat(masked.text()).contains("res-");
        assertThat(masked.aliases()).containsKeys("10.1.2.3", "db.internal");
        var again = masker.mask("host db.internal ip 10.1.2.3", "ada", masked.aliases());
        assertThat(again.text()).contains(masked.aliases().get("db.internal"));
        assertThat(again.aliases()).containsAllEntriesOf(masked.aliases());
    }

    @Test void emptyAliasesStillHideASecret() {
        var masked = masker.mask("api_key=super-secret-value", "ops", Map.of());
        assertThat(masked.text()).doesNotContain("super-secret-value").contains("[REDACTED:TOKEN]");
    }
}
