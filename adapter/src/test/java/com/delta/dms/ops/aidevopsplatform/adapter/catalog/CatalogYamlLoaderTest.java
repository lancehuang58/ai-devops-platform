package com.delta.dms.ops.aidevopsplatform.adapter.catalog;

import com.delta.dms.ops.aidevopsplatform.model.catalog.MonitoredService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogYamlLoaderTest {
    private static final Map<String, String> SECRETS = Map.of(
            "GITLAB_TOKEN", "gitlab-secret-value",
            "JENKINS_TOKEN", "jenkins-secret-value",
            "GLOVE_TOKEN", "glove-secret-value",
            "JIRA_TOKEN", "jira-secret-value",
            "TEAMS_WEBHOOK", "teams-secret-value",
            "LITELLM_API_KEY", "litellm-secret-value",
            "GITLAB_OAUTH_CLIENT_ID", "oauth-client",
            "GITLAB_OAUTH_CLIENT_SECRET", "oauth-secret-value");

    @Test void exampleCatalogResolvesReferencesAndDoesNotPrintSecrets() throws Exception {
        var loaded = new CatalogYamlLoader().load(Files.readString(example()), SECRETS::get);
        assertThat(loaded.properties().getTemplates()).hasSize(3);
        assertThat(loaded.properties().getInstances()).extracting(PlatformServicesProperties.Instance::getId)
                .containsExactly("gitlab-prod", "jenkins-prod", "vm-jenkins-01");
        assertThat(loaded.properties().getIntegrations()).extracting(PlatformServicesProperties.Integration::getId)
                .contains("jira", "teams", "litellm", "gitlab-oauth", "glove");
        assertThat(loaded.catalog().monitored()).extracting(MonitoredService::id).contains("jenkins-prod");
        assertThat(loaded.properties().getInstances().get(1).getCredential()).isEqualTo("${JENKINS_TOKEN}");
        assertThat(loaded.secrets().require("JENKINS_TOKEN")).isEqualTo("jenkins-secret-value");
        assertThat(loaded.properties().toString()).doesNotContain("jenkins-secret-value", "oauth-secret-value");
        assertThat(loaded.secrets().toString()).doesNotContain("jenkins-secret-value", "oauth-secret-value");
        assertThat(loaded.catalog().toString()).doesNotContain("jenkins-secret-value");
    }

    @Test void literalUnresolvedUnknownTypeUnknownChannelAndDuplicateIdFail() {
        var loader = new CatalogYamlLoader();
        assertThatThrownBy(() -> loader.load(sample("credential: hunter2", "jenkins", "[endpoint]"), SECRETS::get))
                .hasMessageContaining("${");
        assertThatThrownBy(() -> loader.load(sample("credential: ${JENKINS_TOKEN}", "jenkins", "[endpoint]"), name -> null))
                .hasMessageContaining("${JENKINS_TOKEN}")
                .hasMessageNotContaining("jenkins-secret-value");
        assertThatThrownBy(() -> loader.load(sample("credential: ${JENKINS_TOKEN}", "not-a-type", "[endpoint]"), SECRETS::get))
                .hasMessageContaining("unknown template type").hasMessageContaining("not-a-type");
        assertThatThrownBy(() -> loader.load(sample("credential: ${JENKINS_TOKEN}", "jenkins", "[snmp]"), SECRETS::get))
                .hasMessageContaining("unknown probe channel");
        assertThatThrownBy(() -> loader.load("""
                platform:
                  services:
                    templates:
                      - id: jenkins-default
                        type: jenkins
                        probes: [endpoint]
                        interval: 60s
                    instances:
                      - id: jenkins-prod
                        type: jenkins
                        environment: prod
                        endpoint-url: https://jenkins.example.internal/login
                        credential: ${JENKINS_TOKEN}
                      - id: jenkins-prod
                        type: jenkins
                        environment: prod
                        endpoint-url: https://jenkins.example.internal/login
                        credential: ${JENKINS_TOKEN}
                """, SECRETS::get)).hasMessageContaining("duplicate id").hasMessageContaining("jenkins-prod");
    }

    private static String sample(String credential, String type, String probes) {
        return """
                platform:
                  services:
                    templates:
                      - id: jenkins-default
                        type: jenkins
                        probes: %s
                        interval: 60s
                    instances:
                      - id: jenkins-prod
                        type: %s
                        environment: prod
                        endpoint-url: https://jenkins.example.internal/login
                        %s
                """.formatted(probes, type, credential);
    }

    private static Path example() {
        var direct = Path.of("config", "platform-services.yml");
        return Files.exists(direct) ? direct : Path.of("..", "config", "platform-services.yml");
    }
}
