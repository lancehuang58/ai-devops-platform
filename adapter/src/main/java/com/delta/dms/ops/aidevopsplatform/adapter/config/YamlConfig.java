package com.delta.dms.ops.aidevopsplatform.adapter.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class YamlConfig {
    private YamlConfig() {}
    public static Map<String,Object> read(Path file) {
        try {
            if (Files.size(file) > 1_048_576) throw new IllegalArgumentException("Config exceeds 1 MiB");
            var options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(20); options.setCodePointLimit(1_048_576);
            try (var input = Files.newInputStream(file)) {
                return map(new Yaml(new SafeConstructor(options)).load(input));
            }
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read configuration file: " + file, e); }
    }
    public static Map<String,Object> map(Object value) {
        if (!(value instanceof Map<?,?> m)) throw new IllegalArgumentException("YAML object required");
        var result = new LinkedHashMap<String,Object>();
        m.forEach((k,v) -> { if (!(k instanceof String)) throw new IllegalArgumentException("String key required"); result.put((String)k,v); });
        return result;
    }
    public static List<?> list(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof List<?> list)) throw new IllegalArgumentException("List required: " + key);
        return list;
    }
    public static String text(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof String value) || value.isBlank()) throw new IllegalArgumentException("Text required: " + key);
        return value;
    }
    public static int number(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof Integer n)) throw new IllegalArgumentException("Integer required: " + key);
        return n;
    }
    public static boolean flag(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof Boolean b)) throw new IllegalArgumentException("Boolean required: " + key);
        return b;
    }
    public static void keys(Map<String,Object> map, String... allowed) {
        if (!Set.of(allowed).containsAll(map.keySet())) throw new IllegalArgumentException("Unknown configuration field");
    }
}
