package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** In-memory alert group. Persistence applies the same decisions under a row lock. */
public final class AlertGroup {
    private GroupStatus status = GroupStatus.FIRING;
    private Severity severityCurrent;
    private Severity severityMax;
    private boolean flapping;
    private Instant flapWindowStart;
    private int flapTransitions;
    private long occurrenceCount = 1;
    private long suppressedCount;
    private Instant lastStateChangeAt;
    private Instant recoveringSince;
    private Instant reopenUntil;
    private int epoch = 1;

    private AlertGroup(Severity severity, Instant openedAt) {
        this.severityCurrent = severity;
        this.severityMax = severity;
        this.lastStateChangeAt = openedAt;
    }

    private AlertGroup(AlertGroupState state) {
        this.status = state.status();
        this.severityCurrent = state.severityCurrent();
        this.severityMax = state.severityMax();
        this.flapping = state.flapping();
        this.flapWindowStart = state.flapWindowStart();
        this.flapTransitions = state.flapTransitions();
        this.occurrenceCount = state.occurrenceCount();
        this.suppressedCount = state.suppressedCount();
        this.lastStateChangeAt = state.lastStateChangeAt();
        this.recoveringSince = state.recoveringSince();
        this.reopenUntil = state.reopenUntil();
        this.epoch = state.epoch();
    }

    public static AlertGroup open(Severity severity, Instant openedAt) {
        return new AlertGroup(severity, openedAt);
    }

    public static AlertGroup rehydrate(AlertGroupState state) {
        return new AlertGroup(state);
    }

    public AlertGroupState state() {
        return new AlertGroupState(status, severityCurrent, severityMax, flapping, flapWindowStart, flapTransitions,
                occurrenceCount, suppressedCount, lastStateChangeAt, recoveringSince, reopenUntil, epoch);
    }

    public ApplyResult apply(SignalStatus signal, Severity severity, Instant observedAt, AlertTiming timing, Instant now) {
        if (observedAt.isBefore(lastStateChangeAt) && opposite(signal)) {
            return new ApplyResult(GroupEffect.STALE_IGNORED, List.of(), false);
        }
        if (signal == SignalStatus.FIRING && status == GroupStatus.FIRING) {
            occurrenceCount++;
            suppressedCount++;
            if (severity.outranks(severityCurrent)) {
                severityCurrent = severity;
                if (severity.outranks(severityMax)) severityMax = severity;
                return new ApplyResult(GroupEffect.SEVERITY_ESCALATED, List.of("SEVERITY_ESCALATED"), false);
            }
            return new ApplyResult(GroupEffect.COUNTED, List.of(), false);
        }
        if (signal == SignalStatus.RESOLVED && (status == GroupStatus.RECOVERED || status == GroupStatus.RECOVERING)) {
            return new ApplyResult(GroupEffect.IGNORED, List.of(), false);
        }
        if (signal == SignalStatus.RESOLVED && status == GroupStatus.FIRING) {
            var started = noteSwitch(now, timing);
            lastStateChangeAt = observedAt;
            if (flapping) {
                status = GroupStatus.RECOVERING;
                recoveringSince = now;
                var notes = new ArrayList<String>();
                if (started) notes.add("FLAPPING_STARTED");
                return new ApplyResult(GroupEffect.RECOVERING, List.copyOf(notes), false);
            }
            status = GroupStatus.RECOVERED;
            reopenUntil = now.plus(timing.reopenWindow());
            return new ApplyResult(GroupEffect.RECOVERED, List.of("RECOVERED"), false);
        }
        if (signal == SignalStatus.FIRING && (status == GroupStatus.RECOVERED || status == GroupStatus.RECOVERING)) {
            var late = reopenUntil != null && observedAt.isAfter(reopenUntil);
            var started = noteSwitch(now, timing);
            status = GroupStatus.FIRING;
            epoch++;
            occurrenceCount++;
            lastStateChangeAt = observedAt;
            recoveringSince = null;
            if (severity.outranks(severityCurrent)) severityCurrent = severity;
            if (flapping) {
                var notes = new ArrayList<String>();
                if (started) notes.add("FLAPPING_STARTED");
                return new ApplyResult(GroupEffect.REFIRED, List.copyOf(notes), late);
            }
            return new ApplyResult(GroupEffect.REFIRED, List.of("REFIRED"), late);
        }
        return new ApplyResult(GroupEffect.IGNORED, List.of(), false);
    }

    public ApplyResult confirmStable(Instant now, AlertTiming timing) {
        if (status != GroupStatus.RECOVERING || recoveringSince == null) {
            return new ApplyResult(GroupEffect.IGNORED, List.of(), false);
        }
        if (now.isBefore(recoveringSince.plus(timing.stableBeforeRecover()))) {
            return new ApplyResult(GroupEffect.IGNORED, List.of(), false);
        }
        status = GroupStatus.RECOVERED;
        lastStateChangeAt = now;
        reopenUntil = now.plus(timing.reopenWindow());
        flapping = false;
        return new ApplyResult(GroupEffect.RECOVERED, List.of("FLAPPING_ENDED", "RECOVERED"), false);
    }

    public GroupStatus status() { return status; }
    public Severity severityCurrent() { return severityCurrent; }
    public boolean flapping() { return flapping; }
    public long occurrenceCount() { return occurrenceCount; }
    public long suppressedCount() { return suppressedCount; }
    public Instant reopenUntil() { return reopenUntil; }
    public int epoch() { return epoch; }

    private boolean opposite(SignalStatus signal) {
        var firingNow = status == GroupStatus.FIRING;
        return firingNow ? signal == SignalStatus.RESOLVED : signal == SignalStatus.FIRING;
    }

    private boolean noteSwitch(Instant now, AlertTiming timing) {
        if (flapWindowStart == null || now.isAfter(flapWindowStart.plus(timing.flapWindow()))) {
            flapWindowStart = now;
            flapTransitions = 1;
        } else flapTransitions++;
        if (!flapping && flapTransitions >= timing.flapThreshold()) {
            flapping = true;
            return true;
        }
        return false;
    }
}
