package com.delta.dms.ops.aidevopsplatform.adapter.access;

import com.delta.dms.ops.aidevopsplatform.service.port.out.RoleDirectory;
import com.delta.dms.ops.aidevopsplatform.service.port.out.QueryAuthorization;
import java.nio.file.Path;
import java.util.*;
import static com.delta.dms.ops.aidevopsplatform.adapter.config.YamlConfig.*;

public final class ConfiguredRoles implements RoleDirectory, QueryAuthorization {
    private final Map<String,Set<String>> assignments;
    public ConfiguredRoles(Path file) {
        var root=read(file); keys(root,"schema-version","accounts");
        if(number(root,"schema-version") != 1) throw new IllegalArgumentException("Unsupported role schema");
        var loaded=new HashMap<String,Set<String>>();
        for(var item:list(root,"accounts")) {
            var row=map(item); keys(row,"username","role"); var role=text(row,"role");
            if(!Set.of("ADMIN","NORMAL_USER").contains(role)) throw new IllegalArgumentException("Unknown role");
            if(loaded.putIfAbsent(text(row,"username"),Set.of(role)) != null) throw new IllegalArgumentException("Duplicate role assignment");
        }
        assignments=Map.copyOf(loaded);
    }
    @Override public Set<String> rolesFor(String username) { return assignments.getOrDefault(username,Set.of()); }
    @Override public void requireAllowed(String actor, String resource) {
        if(rolesFor(actor).isEmpty()) throw new SecurityException("Query permission denied");
    }
}
