package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** fp-v1: environment, resource, symptom and sorted discriminator. Severity and source are excluded. */
public final class Fingerprint {
    public static final String VERSION = "fp-v1";

    private Fingerprint() {}

    public static String of(String environment, String resourceId, String symptomType, Map<String, String> discriminator) {
        var canonical = discriminator.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Normalizer.normalize(entry.getValue().trim(), Normalizer.Form.NFC))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        var input = VERSION + "\n" + environment.toUpperCase(Locale.ROOT) + "\n" + resourceId + "\n" + symptomType + "\n" + canonical;
        return sha256(input);
    }

    public static String unresolvedResourceId(String sourceId, Map<String, String> identityLabels) {
        var identity = identityLabels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return "unresolved:" + sourceId + ":" + sha256(identity).substring(0, 16);
    }

    public static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
