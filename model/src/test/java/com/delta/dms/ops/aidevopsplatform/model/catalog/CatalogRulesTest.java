package com.delta.dms.ops.aidevopsplatform.model.catalog;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogRulesTest {
    private final ServiceTemplate jenkins = new ServiceTemplate("jenkins-default", "jenkins",
            List.of(ProbeChannel.ENDPOINT, ProbeChannel.API, ProbeChannel.VM), Duration.ofSeconds(60),
            Map.of("disk-percent", "90"));

    @Test void missingFieldsUnknownTemplateTypeAndDuplicateIdsFailFast() {
        assertThatThrownBy(() -> CatalogRules.validate(catalog(jenkins, instance("", "jenkins", "prod", "https://jenkins", null, null, null))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> CatalogRules.validate(catalog(jenkins, instance("jenkins-prod", "jenkins", "", "https://jenkins", null, null, null))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("environment");
        assertThatThrownBy(() -> CatalogRules.validate(catalog(jenkins, instance("jenkins-prod", "not-a-type", "prod", "https://jenkins", null, null, null))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("unknown template type").hasMessageContaining("not-a-type");
        var dup = instance("jenkins-prod", "jenkins", "prod", "https://jenkins", "https://jenkins/api", null, "vm-jenkins-01");
        assertThatThrownBy(() -> CatalogRules.validate(new ServiceCatalog(List.of(jenkins), List.of(dup, dup), List.of())))
                .isInstanceOf(CatalogException.class).hasMessageContaining("jenkins-prod");
        assertThatThrownBy(() -> CatalogRules.validate(new ServiceCatalog(List.of(jenkins, jenkins), List.of(dup), List.of())))
                .isInstanceOf(CatalogException.class).hasMessageContaining("jenkins-default");
    }

    @Test void credentialsMustBeReferencesAndTargetsMustMatchConfiguredProbes() {
        var literal = instance("jenkins-prod", "jenkins", "prod", "https://jenkins", "https://jenkins/api", "hunter2", "vm-jenkins-01");
        assertThatThrownBy(() -> CatalogRules.validate(catalog(jenkins, literal)))
                .isInstanceOf(CatalogException.class).hasMessageContaining("${");
        var missingGlove = new ServiceTemplate("vm-default", "vm", List.of(ProbeChannel.VM), Duration.ofSeconds(120), Map.of());
        assertThatThrownBy(() -> CatalogRules.validate(catalog(missingGlove, instance("vm-1", "vm", "prod", null, null, null, null))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("glove");
        var validated = CatalogRules.validate(catalog(jenkins, instance("jenkins-prod", "jenkins", "prod",
                "https://jenkins", "https://jenkins/api", "${JENKINS_TOKEN}", "vm-jenkins-01")));
        assertThat(validated.monitored().getFirst().credentialRef()).isEqualTo("${JENKINS_TOKEN}");
        assertThat(validated.toString()).doesNotContain("hunter2");
    }

    @Test void unknownIntegrationTypeAndLiteralIntegrationSecretFail() {
        var instance = instance("jenkins-prod", "jenkins", "prod", "https://jenkins", "https://jenkins/api", "${JENKINS_TOKEN}", "vm-jenkins-01");
        var pager = new IntegratedService("pager", "pagerduty", "https://pager.example", Map.of("credential", "${PAGER_TOKEN}"), Map.of());
        assertThatThrownBy(() -> CatalogRules.validate(new ServiceCatalog(List.of(jenkins), List.of(instance), List.of(pager))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("pagerduty");
        var leaked = new IntegratedService("jira", "jira", "https://jira.example", Map.of("credential", "literal-token"), Map.of());
        assertThatThrownBy(() -> CatalogRules.validate(new ServiceCatalog(List.of(jenkins), List.of(instance), List.of(leaked))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("${");
        var oauth = new IntegratedService("gitlab-oauth", "gitlab-oauth", "https://gitlab.example",
                Map.of("client-id", "${GITLAB_OAUTH_CLIENT_ID}"), Map.of());
        assertThatThrownBy(() -> CatalogRules.validate(new ServiceCatalog(List.of(jenkins), List.of(instance), List.of(oauth))))
                .isInstanceOf(CatalogException.class).hasMessageContaining("client-secret");
    }

    @Test void probeOverrideKeepsTheFixedPriorityOrder() {
        var overridden = new MonitoredService("jenkins-prod", "jenkins", "prod", "https://jenkins", null, null,
                "vm-jenkins-01", "${JENKINS_TOKEN}", List.of(ProbeChannel.VM, ProbeChannel.ENDPOINT), null, Map.of());
        var catalog = CatalogRules.validate(catalog(jenkins, overridden));
        assertThat(CatalogRules.target(catalog, "jenkins-prod").channels())
                .containsExactly(ProbeChannel.ENDPOINT, ProbeChannel.VM);
    }

    private static ServiceCatalog catalog(ServiceTemplate template, MonitoredService instance) {
        return new ServiceCatalog(List.of(template), List.of(instance), List.of());
    }

    private static MonitoredService instance(String id, String type, String environment, String endpoint, String api,
                                             String credential, String gloveHost) {
        return new MonitoredService(id, type, environment, endpoint, api, null, gloveHost, credential, null, null, Map.of());
    }
}
