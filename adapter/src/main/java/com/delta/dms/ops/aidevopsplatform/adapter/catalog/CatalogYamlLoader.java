package com.delta.dms.ops.aidevopsplatform.adapter.catalog;

import com.delta.dms.ops.aidevopsplatform.model.catalog.CatalogException;
import com.delta.dms.ops.aidevopsplatform.model.catalog.CatalogRules;
import com.delta.dms.ops.aidevopsplatform.model.catalog.IntegratedService;
import com.delta.dms.ops.aidevopsplatform.model.catalog.MonitoredService;
import com.delta.dms.ops.aidevopsplatform.model.catalog.ServiceCatalog;
import com.delta.dms.ops.aidevopsplatform.model.catalog.ServiceTemplate;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.security.SecretRef;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Binds catalog YAML without resolving placeholders, then checks every secret reference. */
public final class CatalogYamlLoader {
    public LoadedCatalog load(String yaml, Function<String, String> environment) {
        if (yaml == null || yaml.isBlank()) throw new CatalogException("catalog YAML is required");
        var properties = bind(yaml);
        var catalog = CatalogRules.validate(toCatalog(properties));
        return new LoadedCatalog(properties, resolve(catalog, environment), catalog);
    }

    private static PlatformServicesProperties bind(String yaml) {
        try {
            var sources = new YamlPropertySourceLoader().load("catalog",
                    new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
            var binder = new Binder(ConfigurationPropertySources.from(sources), PlaceholdersResolver.NONE);
            return binder.bind("platform.services", Bindable.of(PlatformServicesProperties.class))
                    .orElseThrow(() -> new CatalogException("missing platform.services"));
        } catch (IOException ex) {
            throw new CatalogException("catalog YAML could not be read");
        }
    }

    private static ServiceCatalog toCatalog(PlatformServicesProperties properties) {
        var templates = new ArrayList<ServiceTemplate>();
        for (var template : properties.getTemplates()) {
            templates.add(new ServiceTemplate(template.getId(), template.getType(), channels(template.getProbes()),
                    interval(template.getInterval(), template.getId()), map(template.getThresholds())));
        }
        var instances = new ArrayList<MonitoredService>();
        for (var instance : properties.getInstances()) {
            var override = instance.getProbes() == null || instance.getProbes().isEmpty() ? null : channels(instance.getProbes());
            instances.add(new MonitoredService(instance.getId(), instance.getType(), instance.getEnvironment(),
                    instance.getEndpointUrl(), instance.getApiUrl(), instance.getMcpServer(), instance.getGloveHost(),
                    instance.getCredential(), override, intervalOrNull(instance.getInterval()), map(instance.getThresholds())));
        }
        var integrations = new ArrayList<IntegratedService>();
        for (var integration : properties.getIntegrations()) {
            var secrets = new LinkedHashMap<String, String>();
            put(secrets, "credential", integration.getCredential());
            put(secrets, "client-id", integration.getClientId());
            put(secrets, "client-secret", integration.getClientSecret());
            integrations.add(new IntegratedService(integration.getId(), integration.getType(), integration.getBaseUrl(),
                    secrets, map(integration.getSettings())));
        }
        return new ServiceCatalog(templates, instances, integrations);
    }

    private static SecretBundle resolve(ServiceCatalog catalog, Function<String, String> environment) {
        var values = new LinkedHashMap<String, String>();
        for (var instance : catalog.monitored()) remember(values, instance.credentialRef(), environment);
        for (var integration : catalog.integrated()) {
            for (var reference : integration.secrets().values()) remember(values, reference, environment);
        }
        return new SecretBundle(values);
    }

    private static void remember(Map<String, String> values, String reference, Function<String, String> environment) {
        if (reference == null || reference.isBlank()) return;
        var secret = SecretRef.parse(reference);
        values.put(secret.name(), secret.resolve(environment));
    }

    private static List<ProbeChannel> channels(List<String> probes) {
        if (probes == null) return List.of();
        try {
            return probes.stream().map(ProbeChannel::parse).toList();
        } catch (IllegalArgumentException ex) {
            throw new CatalogException(ex.getMessage());
        }
    }

    private static Duration interval(String raw, String owner) {
        if (raw == null || raw.isBlank()) throw new CatalogException("missing interval for " + owner);
        return parse(raw);
    }

    private static Duration intervalOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : parse(raw);
    }

    private static Duration parse(String raw) {
        var text = raw.trim();
        try {
            if (text.startsWith("P")) return Duration.parse(text);
            if (text.endsWith("ms")) return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2).trim()));
            if (text.endsWith("s")) return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1).trim()));
            if (text.endsWith("m")) return Duration.ofMinutes(Long.parseLong(text.substring(0, text.length() - 1).trim()));
        } catch (RuntimeException ex) {
            throw new CatalogException("invalid interval " + raw);
        }
        throw new CatalogException("invalid interval " + raw);
    }

    private static Map<String, String> map(Map<String, String> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    private static void put(Map<String, String> secrets, String key, String value) {
        if (value != null && !value.isBlank()) secrets.put(key, value);
    }
}
