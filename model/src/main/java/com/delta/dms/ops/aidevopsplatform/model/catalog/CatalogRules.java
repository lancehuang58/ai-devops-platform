package com.delta.dms.ops.aidevopsplatform.model.catalog;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChain;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.model.security.SecretRef;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CatalogRules {
    private static final Set<String> INTEGRATED = Set.of("jira", "teams", "litellm", "gitlab-oauth", "mcp");

    private CatalogRules() {}

    public static ServiceCatalog validate(ServiceCatalog catalog) {
        var templates = catalog.templates() == null ? List.<ServiceTemplate>of() : catalog.templates();
        var monitored = catalog.monitored() == null ? List.<MonitoredService>of() : catalog.monitored();
        var integrated = catalog.integrated() == null ? List.<IntegratedService>of() : catalog.integrated();
        var ids = new HashSet<String>();
        var types = new HashSet<String>();
        for (var template : templates) {
            required(template.id(), "template id");
            required(template.type(), "template type");
            unique(ids, template.id());
            if (!types.add(template.type())) throw new CatalogException("duplicate template type " + template.type());
            if (template.probes() == null || template.probes().isEmpty()) {
                throw new CatalogException("missing probes for " + template.id());
            }
            if (template.interval() == null || template.interval().isZero() || template.interval().isNegative()) {
                throw new CatalogException("missing interval for " + template.id());
            }
        }
        for (var instance : monitored) {
            if (instance.id() == null || instance.id().isBlank()) throw new CatalogException("missing field id");
            unique(ids, instance.id());
            if (instance.environment() == null || instance.environment().isBlank()) {
                throw new CatalogException("missing field environment for " + instance.id());
            }
            if (instance.type() == null || instance.type().isBlank() || !types.contains(instance.type())) {
                throw new CatalogException("unknown template type " + instance.type());
            }
            reference(instance.credentialRef());
            var template = templates.stream().filter(item -> item.type().equals(instance.type())).findFirst().orElseThrow();
            requireTargets(instance, channels(template, instance));
        }
        for (var item : integrated) {
            required(item.id(), "integration id");
            unique(ids, item.id());
            if (item.type() == null || !INTEGRATED.contains(item.type())) {
                throw new CatalogException("unknown integration type " + item.type());
            }
            required(item.baseUrl(), "base-url for " + item.id());
            var secrets = item.secrets() == null ? Map.<String, String>of() : item.secrets();
            if ("gitlab-oauth".equals(item.type())) {
                requireSecret(secrets, "client-id");
                requireSecret(secrets, "client-secret");
            } else {
                requireSecret(secrets, "credential");
            }
        }
        return new ServiceCatalog(List.copyOf(templates), List.copyOf(monitored), List.copyOf(integrated));
    }

    public static ProbeTarget target(ServiceCatalog catalog, String instanceId) {
        var validated = catalog.monitored().stream().anyMatch(item -> item.id().equals(instanceId))
                ? catalog : validate(catalog);
        var instance = validated.monitored().stream().filter(item -> item.id().equals(instanceId)).findFirst()
                .orElseThrow(() -> new CatalogException("unknown instance " + instanceId));
        var template = validated.templates().stream().filter(item -> item.type().equals(instance.type())).findFirst()
                .orElseThrow(() -> new CatalogException("unknown template type " + instance.type()));
        return new ProbeTarget(instance.id(), instance.type(), instance.environment(), channels(template, instance),
                instance.endpointUrl(), instance.apiUrl(), instance.mcpServerId(), instance.gloveHost());
    }

    private static List<ProbeChannel> channels(ServiceTemplate template, MonitoredService instance) {
        var selected = instance.probeOverride() == null || instance.probeOverride().isEmpty()
                ? template.probes() : instance.probeOverride();
        return ProbeChain.order(selected);
    }

    private static void requireTargets(MonitoredService instance, List<ProbeChannel> channels) {
        if (channels.contains(ProbeChannel.ENDPOINT)) required(instance.endpointUrl(), "endpoint url for " + instance.id());
        if (channels.contains(ProbeChannel.API)) required(instance.apiUrl(), "api url for " + instance.id());
        if (channels.contains(ProbeChannel.MCP)) required(instance.mcpServerId(), "mcp server for " + instance.id());
        if (channels.contains(ProbeChannel.VM)) required(instance.gloveHost(), "glove host for " + instance.id());
    }

    private static void requireSecret(Map<String, String> secrets, String key) {
        if (!secrets.containsKey(key) || secrets.get(key) == null || secrets.get(key).isBlank()) {
            throw new CatalogException("missing field " + key);
        }
        reference(secrets.get(key));
    }

    private static void reference(String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            SecretRef.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw new CatalogException(ex.getMessage());
        }
    }

    private static void unique(Set<String> ids, String id) {
        if (!ids.add(id)) throw new CatalogException("duplicate id " + id);
    }

    private static void required(String value, String field) {
        if (value == null || value.isBlank()) throw new CatalogException("missing field " + field);
    }
}
