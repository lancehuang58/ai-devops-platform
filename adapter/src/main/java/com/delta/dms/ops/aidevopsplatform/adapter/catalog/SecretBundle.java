package com.delta.dms.ops.aidevopsplatform.adapter.catalog;

import java.util.Map;

/** Resolved secret values. Text form lists reference names and never the values. */
public final class SecretBundle {
    private final Map<String, String> values;

    SecretBundle(Map<String, String> values) { this.values = Map.copyOf(values); }

    public String require(String name) {
        var value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("unresolved secret reference ${" + name + "}");
        }
        return value;
    }

    @Override public String toString() { return "SecretBundle{" + values.keySet() + "}"; }
}
