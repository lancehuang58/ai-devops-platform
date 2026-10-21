package com.delta.dms.ops.aidevopsplatform.adapter.config;

import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ManagedServiceRepository;
import java.nio.file.Path;
import java.net.URI;
import java.util.*;
import static com.delta.dms.ops.aidevopsplatform.adapter.config.YamlConfig.*;

/** Read a validated startup snapshot from one authoritative catalog file. */
public final class YamlManagedServiceRepository implements ManagedServiceRepository {
    private final Map<String,ManagedService> services;
    public YamlManagedServiceRepository(Path file) {
        var root = read(file); keys(root,"schema-version","services");
        if (number(root,"schema-version") != 1) throw new IllegalArgumentException("Unsupported catalog schema");
        var loaded = new LinkedHashMap<String,ManagedService>();
        for (var item : list(root,"services")) {
            var row = map(item); keys(row,"id","name","kind","environment","enabled","connection","health-profile","timeout-seconds");
            var c = map(row.get("connection")); keys(c,"transport","endpoint","credential-ref");
            var service = new ManagedService(text(row,"id"),text(row,"name"),ManagedService.Kind.valueOf(text(row,"kind")),
                    text(row,"environment"),flag(row,"enabled"),
                    new ManagedService.Connection(ManagedService.Transport.valueOf(text(c,"transport")),URI.create(text(c,"endpoint")),
                            c.containsKey("credential-ref") ? text(c,"credential-ref") : null),
                    text(row,"health-profile"),number(row,"timeout-seconds"));
            if (loaded.putIfAbsent(service.id(),service) != null) throw new IllegalArgumentException("Duplicate service ID");
        }
        services = Collections.unmodifiableMap(loaded);
    }
    @Override public List<ManagedService> findAll() { return List.copyOf(services.values()); }
    @Override public Optional<ManagedService> findById(String id) { return Optional.ofNullable(services.get(id)); }
}
