package com.delta.dms.ops.aidevopsplatform.model.takeover;

import com.delta.dms.ops.aidevopsplatform.model.alert.Fingerprint;
import com.delta.dms.ops.aidevopsplatform.model.security.SecretFilter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class ExportMasker {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern HOST = Pattern.compile("\\b(?:[a-z0-9-]+\\.)+(?:internal|local)\\b");

    public MaskedText mask(String text, String operator) {
        return mask(text, operator, Map.of());
    }

    public MaskedText mask(String text, String operator, Map<String, String> existingAliases) {
        var aliases = new LinkedHashMap<String, String>();
        if (existingAliases != null) aliases.putAll(existingAliases);
        var masked = SecretFilter.redact(text == null ? "" : text);
        masked = replace(EMAIL, masked, aliases, value -> "user-" + Fingerprint.sha256(value).substring(0, 6));
        masked = replace(IPV4, masked, aliases, value -> alias(value, aliases));
        masked = replace(HOST, masked, aliases, value -> alias(value, aliases));
        return new MaskedText(masked, Map.copyOf(aliases));
    }

    private static String alias(String value, Map<String, String> aliases) {
        return aliases.computeIfAbsent(value, key -> "res-" + Fingerprint.sha256(key).substring(0, 6));
    }

    private static String replace(Pattern pattern, String text, Map<String, String> aliases, java.util.function.Function<String, String> token) {
        var matcher = pattern.matcher(text);
        var out = new StringBuilder();
        while (matcher.find()) {
            var raw = matcher.group();
            var replacement = token.apply(raw);
            if (pattern == IPV4 || pattern == HOST) aliases.putIfAbsent(raw, replacement);
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
