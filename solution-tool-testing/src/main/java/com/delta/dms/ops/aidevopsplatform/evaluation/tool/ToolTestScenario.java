package com.delta.dms.ops.aidevopsplatform.evaluation.tool;

import java.util.Objects;

/** Test plan metadata, not a replacement for the real Dispatcher authorization. */
public record ToolTestScenario(String caseId, Mode mode, Environment environment) {
    public enum Mode { OFFLINE, TEST_SERVICE }
    public enum Environment { LOCAL, TEST, PRODUCTION }
    public ToolTestScenario {
        Objects.requireNonNull(mode); Objects.requireNonNull(environment);
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId required");
        if (environment == Environment.PRODUCTION)
            throw new IllegalArgumentException("Production is not a tool-test environment");
        if (mode == Mode.TEST_SERVICE && environment != Environment.TEST)
            throw new IllegalArgumentException("TEST_SERVICE requires an isolated test environment");
    }
}
