package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import java.time.Instant;
import java.util.Objects;

public record Observation(Status status, String version, Instant observedAt, boolean mocked) {
    public enum Status { UP, DOWN, UNKNOWN }
    public Observation {
        Objects.requireNonNull(status); Objects.requireNonNull(observedAt);
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Version required");
    }
}
