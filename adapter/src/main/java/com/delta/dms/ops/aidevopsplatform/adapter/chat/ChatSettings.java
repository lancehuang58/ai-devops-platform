package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import com.delta.dms.ops.aidevopsplatform.model.security.SecretRef;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.util.*;
import java.util.function.Function;

/** Raw YAML is loaded before Spring placeholder expansion so literal credentials cannot slip through. */
public record ChatSettings(String model, String baseUrl, String apiKey, List<Remote> servers) {
    public record Remote(String id, String url, String token, Map<String, String> bindings) {
        @Override public String toString() { return "Remote[REDACTED]"; }
    }
    public static ChatSettings load(String yaml, Function<String, String> environment) {
        final Map<String, Object> data;
        try {
            var options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            data = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (RuntimeException ex) { throw new IllegalArgumentException("invalid chat YAML; check structure (details redacted)"); }
        if (data == null) throw new IllegalArgumentException("chat settings required");
        var key = SecretRef.parse((String) data.get("api-key")).resolve(environment);
        var remotes = new ArrayList<Remote>();
        var ids = new HashSet<String>();
        for (var item : (List<?>) data.getOrDefault("servers", List.of())) {
            var m = (Map<?, ?>) item;
            var id = (String) m.get("id");
            if (id == null || !ids.add(id)) throw new IllegalArgumentException("duplicate or missing MCP server id");
            String url = checkedUrl((String) m.get("url"));
            String token = m.get("token") == null ? null : SecretRef.parse((String) m.get("token")).resolve(environment);
            var bindings = new HashMap<String, String>();
            if (!(m.get("bindings") instanceof Map<?, ?> raw)) throw new IllegalArgumentException("MCP bindings required");
            raw.forEach((k, v) -> {
                if (!Set.of("catalog", "status", "health").contains(k) || !(v instanceof String name)
                        || !name.matches("[a-zA-Z0-9_.-]{1,100}")) throw new IllegalArgumentException("invalid readonly MCP binding");
                bindings.put((String) k, (String) v);
            });
            if (!Boolean.TRUE.equals(m.get("read-only"))) throw new IllegalArgumentException("MCP server must be explicitly approved read-only");
            remotes.add(new Remote(id, url, token, Map.copyOf(bindings)));
        }
        return new ChatSettings((String) data.getOrDefault("model", "qwen-flash 3.8"),
                checkedUrl((String) data.getOrDefault("base-url", "https://dashscope.aliyuncs.com/compatible-mode/v1")), key, List.copyOf(remotes));
    }
    private static String checkedUrl(String value) {
        var uri = java.net.URI.create(value);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("invalid service URL");
        return value;
    }
    @Override public String toString() { return "ChatSettings[REDACTED]"; }
}
