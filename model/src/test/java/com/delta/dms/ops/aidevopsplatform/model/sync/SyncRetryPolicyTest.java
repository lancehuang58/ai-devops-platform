package com.delta.dms.ops.aidevopsplatform.model.sync;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRetryPolicyTest {
    private static final Instant FAILED_AT = Instant.parse("2026-10-26T00:00:00Z");
    private final SyncRetryPolicy policy = new SyncRetryPolicy();

    @Test void sixCallsThenStop() {
        Duration[] expected = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4)};
        for (int attempt = 1; attempt <= 5; attempt++) {
            var decision = policy.afterTransientFailure(attempt, FAILED_AT, 0.0);
            assertThat(decision.stop()).isFalse();
            assertThat(decision.nextAttemptAt()).isEqualTo(FAILED_AT.plus(expected[attempt - 1]).minus(scale(expected[attempt - 1])));
        }
        var stopped = policy.afterTransientFailure(6, FAILED_AT, 0.0);
        assertThat(stopped.stop()).isTrue();
        assertThat(stopped.nextAttemptAt()).isNull();
    }

    @Test void jitterStaysInsideTwentyPercent() {
        var base = Duration.ofMinutes(5);
        var low = policy.afterTransientFailure(2, FAILED_AT, 0.0);
        var high = policy.afterTransientFailure(2, FAILED_AT, 1.0);
        assertThat(Duration.between(FAILED_AT, low.nextAttemptAt())).isEqualTo(base.minus(scale(base)));
        assertThat(Duration.between(FAILED_AT, high.nextAttemptAt())).isEqualTo(base.plus(scale(base)));
    }

    private static Duration scale(Duration base) {
        return Duration.ofMillis(Math.round(base.toMillis() * 0.2));
    }
}
