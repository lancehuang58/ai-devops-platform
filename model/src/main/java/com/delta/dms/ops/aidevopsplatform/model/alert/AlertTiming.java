package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.time.Duration;

public record AlertTiming(Duration flapWindow, int flapThreshold, Duration stableBeforeRecover, Duration reopenWindow) {
    public static AlertTiming defaults() {
        return new AlertTiming(Duration.ofMinutes(30), 3, Duration.ofMinutes(15), Duration.ofMinutes(30));
    }
}
