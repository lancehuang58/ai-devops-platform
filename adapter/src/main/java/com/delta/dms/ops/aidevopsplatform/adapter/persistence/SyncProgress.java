package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import java.time.Instant;

public record SyncProgress(boolean stop, int attempts, Instant nextAttemptAt) {
}
