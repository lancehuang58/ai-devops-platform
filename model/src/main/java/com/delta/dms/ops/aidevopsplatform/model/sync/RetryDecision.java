package com.delta.dms.ops.aidevopsplatform.model.sync;

import java.time.Instant;

public record RetryDecision(boolean stop, Instant nextAttemptAt) {
}
