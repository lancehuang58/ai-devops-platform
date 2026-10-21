package com.delta.dms.ops.aidevopsplatform.evaluation.model;

import java.util.Objects;

/** Harness correctness is distinct from real-model quality. */
public record ModelEvaluationResult(String caseId, Mode mode, Status status) {
    public enum Mode { STUB, REAL_QWEN, REAL_EXTERNAL }
    public enum Status { NOT_RUN, HARNESS_ONLY, BLOCKED_ENV, PASS, FAIL }
    public ModelEvaluationResult {
        Objects.requireNonNull(mode); Objects.requireNonNull(status);
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId required");
        if (mode == Mode.STUB && status == Status.PASS)
            throw new IllegalArgumentException("A stub cannot pass model quality evaluation");
        if (mode != Mode.STUB && status == Status.HARNESS_ONLY)
            throw new IllegalArgumentException("HARNESS_ONLY requires a stub");
    }
}
