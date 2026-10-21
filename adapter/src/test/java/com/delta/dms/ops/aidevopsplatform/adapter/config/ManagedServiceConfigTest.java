package com.delta.dms.ops.aidevopsplatform.adapter.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class ManagedServiceConfigTest {
    @TempDir Path temp;
    private Path config(String extra) throws Exception {
        var file=temp.resolve("services.yml");
        Files.writeString(file,"""
schema-version: 1
services:
  - id: jenkins-demo
    name: Demo
    kind: JENKINS
    environment: TEST
    enabled: true
    connection:
      transport: MOCK
      endpoint: https://jenkins.invalid
    health-profile: jenkins-basic-v1
    timeout-seconds: 5
"""+extra);return file;
    }
    @Test void catalogLoadsSeparatelyFromBootstrapConfiguration() throws Exception {
        var repository=new YamlManagedServiceRepository(config(""));
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById("jenkins-demo").orElseThrow().name()).isEqualTo("Demo");
    }
    @Test void duplicateKeysFailInsteadOfSilentlyReplacingData() throws Exception {
        var file=config("schema-version: 2\n");
        assertThatThrownBy(()->new YamlManagedServiceRepository(file)).isInstanceOf(RuntimeException.class);
    }
    @Test void unknownFieldsAreRejected() throws Exception {
        var file=config("unexpected: true\n");
        assertThatThrownBy(()->new YamlManagedServiceRepository(file)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingConfigDoesNotCreateAnEmptyFallbackCatalog() {
        assertThatThrownBy(()->new YamlManagedServiceRepository(temp.resolve("missing.yml"))).isInstanceOf(IllegalArgumentException.class);
    }
}
