package com.delta.dms.ops.aidevopsplatform.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogStartupTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogConfiguration.class)
            .withPropertyValues("spring.profiles.active=platform",
                    "platform.catalog.file=../config/platform-services.yml");

    @Test void unresolvedSecretReferenceFailsStartup() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("${");
        });
    }

    @Test void exampleCatalogStartsWhenEveryReferenceResolves() {
        runner.withPropertyValues(
                "GITLAB_TOKEN=test", "JENKINS_TOKEN=test", "GLOVE_TOKEN=test", "JIRA_TOKEN=test",
                "TEAMS_WEBHOOK=test", "LITELLM_API_KEY=test",
                "GITLAB_OAUTH_CLIENT_ID=test", "GITLAB_OAUTH_CLIENT_SECRET=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(com.delta.dms.ops.aidevopsplatform.adapter.catalog.SecretBundle.class)
                            .extracting(Object::toString).asString().doesNotContain("=test");
                });
    }
}
