package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import java.time.Instant;

public record ProbePublication(ProbeTarget target, ProbeDecision decision, Instant observedAt) {
}
