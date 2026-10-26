package com.delta.dms.ops.aidevopsplatform.model.monitoring;

public enum ProbeChannel {
    ENDPOINT, MCP, API, VM;

    public static ProbeChannel parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("probe channel is required");
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "endpoint" -> ENDPOINT;
            case "mcp" -> MCP;
            case "api" -> API;
            case "vm", "glove" -> VM;
            default -> throw new IllegalArgumentException("unknown probe channel " + raw);
        };
    }
}
