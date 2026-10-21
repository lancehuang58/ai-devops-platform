package com.delta.dms.ops.aidevopsplatform.web.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class DemoConfigurationTest {
    @Test void realModesCannotSilentlyUseMockAdapters() {
        var settings=new PlatformSettings(new PlatformSettings.Database("postgresql","jdbc:postgresql://db.invalid/test","user","secret"),
                new PlatformSettings.Ldap("mock","ldap://ldap.invalid","dc=test","","",Map.of("normal","secret")),
                new PlatformSettings.Llm("mock","https://qwen.invalid","secret","qwen"),new PlatformSettings.Catalog("yaml","file"),"roles");
        assertThatThrownBy(()->new DemoConfiguration(settings)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("real adapters are not implemented");
        assertThat(settings.toString()).doesNotContain("secret");
    }
}
