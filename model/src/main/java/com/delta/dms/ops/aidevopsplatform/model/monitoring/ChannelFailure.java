package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import java.util.Locale;

/** Names a channel outage without treating an answered unhealthy report as a channel failure. */
public final class ChannelFailure {
    private ChannelFailure() {}

    public static String explain(Throwable error) {
        var type = error.getClass().getName();
        var message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (type.endsWith("SocketTimeoutException") || message.contains("timed out") || message.contains("timeout")) {
            return "timeout";
        }
        if (type.endsWith("ConnectException") || message.contains("connection refused")) return "connection refused";
        if (type.endsWith("UnknownHostException") || message.contains("unknown host")) return "dns";
        if (type.contains("SSL") || type.contains("TLS")) return "tls";
        if (message.contains("mcp")) return "mcp server unavailable";
        if (error instanceof SecurityException || message.contains("unauthorized") || message.contains("auth failure")) {
            return "auth failure";
        }
        return "channel failed";
    }
}
