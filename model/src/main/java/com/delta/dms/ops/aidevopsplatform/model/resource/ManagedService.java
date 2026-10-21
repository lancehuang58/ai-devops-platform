package com.delta.dms.ops.aidevopsplatform.model.resource;

import java.net.URI;
import java.util.Objects;

/** Managed resource configuration; never platform bootstrap configuration. */
public record ManagedService(String id, String name, Kind kind, String environment,
        boolean enabled, Connection connection, String healthProfile, int timeoutSeconds) {
    public enum Kind { JENKINS }
    public enum Transport { MOCK, MCP, API, VM_GATEWAY }
    public record Connection(Transport transport, URI endpoint, String credentialRef) {
        public Connection {
            Objects.requireNonNull(transport); Objects.requireNonNull(endpoint);
            if (!endpoint.isAbsolute() || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null || endpoint.getFragment() != null
                    || !(endpoint.getScheme().equals("https") || endpoint.getScheme().equals("http")))
                throw new IllegalArgumentException("Service endpoint must be an HTTP(S) URL without credentials/query");
        }
    }
    public ManagedService {
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Invalid service ID");
        if (name == null || name.isBlank() || environment == null || environment.isBlank()
                || healthProfile == null || healthProfile.isBlank()) throw new IllegalArgumentException("Service fields required");
        Objects.requireNonNull(kind); Objects.requireNonNull(connection);
        if (timeoutSeconds < 1 || timeoutSeconds > 300) throw new IllegalArgumentException("Invalid probe timeout");
    }
}
