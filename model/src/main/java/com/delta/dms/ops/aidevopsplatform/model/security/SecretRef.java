package com.delta.dms.ops.aidevopsplatform.model.security;

import java.util.function.Function;
import java.util.regex.Pattern;

/** An environment or Kubernetes Secret reference. The value is never part of this object. */
public final class SecretRef {
    private static final Pattern REF = Pattern.compile("^\\$\\{([A-Z][A-Z0-9_]*)}$");
    private final String name;

    private SecretRef(String name) { this.name = name; }

    public static SecretRef parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("secret reference is required");
        var matcher = REF.matcher(raw);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("secret must be an environment reference like ${HARBOR_TOKEN}, not a literal");
        }
        return new SecretRef(matcher.group(1));
    }

    public String name() { return name; }

    public String resolve(Function<String, String> environment) {
        var value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("unresolved secret reference ${" + name + "}");
        }
        return value;
    }

    @Override public String toString() { return "${" + name + "}"; }
}
