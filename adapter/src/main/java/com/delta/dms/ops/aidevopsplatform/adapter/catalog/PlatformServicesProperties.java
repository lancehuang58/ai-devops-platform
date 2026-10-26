package com.delta.dms.ops.aidevopsplatform.adapter.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed catalog. Secret fields keep the ${ENV} reference; values live in {@link SecretBundle}. */
@ConfigurationProperties(prefix = "platform.services")
public class PlatformServicesProperties {
    private List<Template> templates = new ArrayList<>();
    private List<Instance> instances = new ArrayList<>();
    private List<Integration> integrations = new ArrayList<>();

    public List<Template> getTemplates() { return templates; }
    public void setTemplates(List<Template> templates) { this.templates = templates; }
    public List<Instance> getInstances() { return instances; }
    public void setInstances(List<Instance> instances) { this.instances = instances; }
    public List<Integration> getIntegrations() { return integrations; }
    public void setIntegrations(List<Integration> integrations) { this.integrations = integrations; }

    @Override public String toString() {
        return "PlatformServicesProperties{templates=" + ids(templates) + ", instances=" + ids(instances)
                + ", integrations=" + ids(integrations) + "}";
    }

    private static String ids(List<?> items) {
        if (items == null) return "[]";
        var names = new ArrayList<String>();
        for (var item : items) {
            if (item instanceof Template template) names.add(template.getId());
            else if (item instanceof Instance instance) names.add(instance.getId());
            else if (item instanceof Integration integration) names.add(integration.getId());
        }
        return names.toString();
    }

    public static class Template {
        private String id;
        private String type;
        private List<String> probes = new ArrayList<>();
        private String interval;
        private Map<String, String> thresholds = new LinkedHashMap<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<String> getProbes() { return probes; }
        public void setProbes(List<String> probes) { this.probes = probes; }
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
        public Map<String, String> getThresholds() { return thresholds; }
        public void setThresholds(Map<String, String> thresholds) { this.thresholds = thresholds; }
    }

    public static class Instance {
        private String id;
        private String type;
        private String environment;
        private String endpointUrl;
        private String apiUrl;
        private String mcpServer;
        private String gloveHost;
        private String credential;
        private List<String> probes = new ArrayList<>();
        private String interval;
        private Map<String, String> thresholds = new LinkedHashMap<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        public String getEndpointUrl() { return endpointUrl; }
        public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getMcpServer() { return mcpServer; }
        public void setMcpServer(String mcpServer) { this.mcpServer = mcpServer; }
        public String getGloveHost() { return gloveHost; }
        public void setGloveHost(String gloveHost) { this.gloveHost = gloveHost; }
        public String getCredential() { return credential; }
        public void setCredential(String credential) { this.credential = credential; }
        public List<String> getProbes() { return probes; }
        public void setProbes(List<String> probes) { this.probes = probes; }
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
        public Map<String, String> getThresholds() { return thresholds; }
        public void setThresholds(Map<String, String> thresholds) { this.thresholds = thresholds; }
    }

    public static class Integration {
        private String id;
        private String type;
        private String baseUrl;
        private String credential;
        private String clientId;
        private String clientSecret;
        private Map<String, String> settings = new LinkedHashMap<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getCredential() { return credential; }
        public void setCredential(String credential) { this.credential = credential; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public Map<String, String> getSettings() { return settings; }
        public void setSettings(Map<String, String> settings) { this.settings = settings; }
    }
}
