package com.delta.dms.ops.aidevopsplatform.model.alert;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertGroupTest {
    private static final Instant T0 = Instant.parse("2026-10-26T07:30:00Z");
    private static final AlertTiming TIMING = AlertTiming.defaults();

    @Test void repeatedFiringOnlyCountsAndDoesNotNotifyAgain() {
        var group = AlertGroup.open(Severity.MAJOR, T0);
        var again = group.apply(SignalStatus.FIRING, Severity.MAJOR, T0.plusSeconds(30), TIMING, T0.plusSeconds(30));
        assertThat(again.effect()).isEqualTo(GroupEffect.COUNTED);
        assertThat(again.notifications()).isEmpty();
        assertThat(group.occurrenceCount()).isEqualTo(2);
        assertThat(group.suppressedCount()).isEqualTo(1);
        assertThat(group.status()).isEqualTo(GroupStatus.FIRING);
    }

    @Test void higherSeverityNotifiesOnceAndADowngradeDoesNot() {
        var group = AlertGroup.open(Severity.WARNING, T0);
        var up = group.apply(SignalStatus.FIRING, Severity.CRITICAL, T0.plusSeconds(1), TIMING, T0.plusSeconds(1));
        assertThat(up.effect()).isEqualTo(GroupEffect.SEVERITY_ESCALATED);
        assertThat(up.notifications()).containsExactly("SEVERITY_ESCALATED");
        var down = group.apply(SignalStatus.FIRING, Severity.MINOR, T0.plusSeconds(2), TIMING, T0.plusSeconds(2));
        assertThat(down.notifications()).isEmpty();
        assertThat(group.severityCurrent()).isEqualTo(Severity.CRITICAL);
    }

    @Test void resolvedNotifiesOnceAndReopenWindowReturnsToTheSameGroup() {
        var group = AlertGroup.open(Severity.MAJOR, T0);
        var recovered = group.apply(SignalStatus.RESOLVED, Severity.MAJOR, T0.plus(Duration.ofMinutes(5)), TIMING, T0.plus(Duration.ofMinutes(5)));
        assertThat(recovered.effect()).isEqualTo(GroupEffect.RECOVERED);
        assertThat(recovered.notifications()).containsExactly("RECOVERED");
        assertThat(group.reopenUntil()).isEqualTo(T0.plus(Duration.ofMinutes(35)));
        var refired = group.apply(SignalStatus.FIRING, Severity.MAJOR, T0.plus(Duration.ofMinutes(10)), TIMING, T0.plus(Duration.ofMinutes(10)));
        assertThat(refired.effect()).isEqualTo(GroupEffect.REFIRED);
        assertThat(refired.notifications()).containsExactly("REFIRED");
        assertThat(group.epoch()).isEqualTo(2);
        assertThat(refired.lateRefire()).isFalse();
    }

    @Test void threeSwitchesInsideThirtyMinutesAreFlappingAndNeedFifteenStableMinutes() {
        var group = AlertGroup.open(Severity.MAJOR, T0);
        group.apply(SignalStatus.RESOLVED, Severity.MAJOR, T0.plus(Duration.ofMinutes(1)), TIMING, T0.plus(Duration.ofMinutes(1)));
        group.apply(SignalStatus.FIRING, Severity.MAJOR, T0.plus(Duration.ofMinutes(2)), TIMING, T0.plus(Duration.ofMinutes(2)));
        var third = group.apply(SignalStatus.RESOLVED, Severity.MAJOR, T0.plus(Duration.ofMinutes(3)), TIMING, T0.plus(Duration.ofMinutes(3)));
        assertThat(group.flapping()).isTrue();
        assertThat(third.notifications()).contains("FLAPPING_STARTED");
        assertThat(third.notifications()).doesNotContain("RECOVERED");
        assertThat(group.status()).isEqualTo(GroupStatus.RECOVERING);
        var tooSoon = group.confirmStable(T0.plus(Duration.ofMinutes(17)), TIMING);
        assertThat(tooSoon.effect()).isEqualTo(GroupEffect.IGNORED);
        var stable = group.confirmStable(T0.plus(Duration.ofMinutes(18)), TIMING);
        assertThat(stable.effect()).isEqualTo(GroupEffect.RECOVERED);
        assertThat(stable.notifications()).containsExactly("FLAPPING_ENDED", "RECOVERED");
    }

    @Test void refireAfterTheReopenWindowStaysOnTheSameGroupButIsMarkedLate() {
        var group = AlertGroup.open(Severity.MAJOR, T0);
        group.apply(SignalStatus.RESOLVED, Severity.MAJOR, T0.plus(Duration.ofMinutes(1)), TIMING, T0.plus(Duration.ofMinutes(1)));
        var late = group.apply(SignalStatus.FIRING, Severity.MAJOR, T0.plus(Duration.ofMinutes(40)), TIMING, T0.plus(Duration.ofMinutes(40)));
        assertThat(late.effect()).isEqualTo(GroupEffect.REFIRED);
        assertThat(late.lateRefire()).isTrue();
        assertThat(group.status()).isEqualTo(GroupStatus.FIRING);
    }

    @Test void olderOppositeSignalIsStale() {
        var group = AlertGroup.open(Severity.MAJOR, T0);
        group.apply(SignalStatus.RESOLVED, Severity.MAJOR, T0.plus(Duration.ofMinutes(5)), TIMING, T0.plus(Duration.ofMinutes(5)));
        var stale = group.apply(SignalStatus.FIRING, Severity.MAJOR, T0.plus(Duration.ofMinutes(1)), TIMING, T0.plus(Duration.ofMinutes(6)));
        assertThat(stale.effect()).isEqualTo(GroupEffect.STALE_IGNORED);
        assertThat(group.status()).isEqualTo(GroupStatus.RECOVERED);
    }
}
