package com.delta.dms.ops.aidevopsplatform.model.security;

import java.util.regex.Pattern;

/** Replaces secret-shaped text. Callers must not fall back to the original when this runs. */
public final class SecretFilter {
    private static final Pattern AUTHORIZATION = Pattern.compile("(?i)authorization\\s*:\\s*bearer\\s+\\S+");
    private static final Pattern PASSWORD = Pattern.compile("(?i)(password\\s*[=:]\\s*)\\S+");
    private static final Pattern TOKEN = Pattern.compile("(?i)((?:api[_\\s-]?key|token)\\s*[=:]\\s*)\\S+");
    private static final Pattern USERINFO = Pattern.compile("(https?://)([^\\s/:@]+):([^\\s/@]+)@");

    private SecretFilter() {}

    public static String redact(String text) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        var step = AUTHORIZATION.matcher(text).replaceAll("[REDACTED:AUTHORIZATION]");
        step = USERINFO.matcher(step).replaceAll("$1$2:[REDACTED:PASSWORD]@");
        step = PASSWORD.matcher(step).replaceAll("$1[REDACTED:PASSWORD]");
        step = TOKEN.matcher(step).replaceAll("$1[REDACTED:TOKEN]");
        return step;
    }
}
