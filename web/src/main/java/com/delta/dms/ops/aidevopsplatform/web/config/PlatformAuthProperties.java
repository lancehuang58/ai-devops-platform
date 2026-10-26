package com.delta.dms.ops.aidevopsplatform.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.auth")
public class PlatformAuthProperties {
    private String alertToken = "dev-alert-token";
    private Map<String, User> users = new LinkedHashMap<>();

    public String getAlertToken() { return alertToken; }
    public void setAlertToken(String alertToken) { this.alertToken = alertToken; }
    public Map<String, User> getUsers() { return users; }
    public void setUsers(Map<String, User> users) { this.users = users; }

    public static class User {
        private String password = "";
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
