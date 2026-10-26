package com.delta.dms.ops.aidevopsplatform.model.sync;

import java.time.Duration;
import java.time.Instant;

/** First failure plus five retries (six calls). Delay index follows the failed attempt, with ±20% jitter. */
public final class SyncRetryPolicy {
    static final Duration[] DELAYS = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(4)
    };
    public static final int MAX_CALLS = 6;

    public RetryDecision afterTransientFailure(int completedAttempts, Instant failedAt, double jitterUnit) {
        if (completedAttempts >= MAX_CALLS) return new RetryDecision(true, null);
        if (completedAttempts < 1 || completedAttempts > DELAYS.length) {
            throw new IllegalArgumentException("completedAttempts must be 1.." + MAX_CALLS);
        }
        var base = DELAYS[completedAttempts - 1];
        double factor = 0.8d + (0.4d * jitterUnit);
        long millis = Math.round(base.toMillis() * factor);
        return new RetryDecision(false, failedAt.plusMillis(millis));
    }
}
