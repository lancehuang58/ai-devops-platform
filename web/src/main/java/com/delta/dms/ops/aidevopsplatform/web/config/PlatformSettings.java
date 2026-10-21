package com.delta.dms.ops.aidevopsplatform.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix="platform", ignoreUnknownFields=false)
public record PlatformSettings(Database database, Ldap ldap, Llm llm, Catalog catalog, String rolesFile) {
    public record Database(String mode, String jdbcUrl, String username, String password) {
        @Override public String toString() { return "Database[redacted]"; }
    }
    public record Ldap(String mode, String url, String baseDn, String bindDn, String bindPassword, Map<String,String> users) {
        @Override public String toString() { return "Ldap[redacted]"; }
    }
    public record Llm(String mode, String baseUrl, String apiKey, String modelName) {
        @Override public String toString() { return "Llm[redacted]"; }
    }
    public record Catalog(String mode, String file) {}
}
