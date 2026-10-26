package com.delta.dms.ops.aidevopsplatform.model.alert;

public enum Severity {
    INFO, WARNING, MINOR, MAJOR, CRITICAL;

    public boolean outranks(Severity other) { return ordinal() > other.ordinal(); }

    public static Severity parse(String raw) {
        if (raw == null || raw.isBlank()) return WARNING;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "critical" -> CRITICAL;
            case "major", "error" -> MAJOR;
            case "minor" -> MINOR;
            case "warning" -> WARNING;
            case "info", "none" -> INFO;
            default -> WARNING;
        };
    }
}
