package com.delta.dms.ops.aidevopsplatform.model.alert;

public enum SignalStatus {
    FIRING, RESOLVED;

    public static SignalStatus parse(String raw) {
        if (raw == null) throw new InvalidAlertException("status is required");
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "firing" -> FIRING;
            case "resolved" -> RESOLVED;
            default -> throw new InvalidAlertException("unsupported alert status " + raw);
        };
    }
}
