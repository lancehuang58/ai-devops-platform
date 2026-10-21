package com.delta.dms.ops.aidevopsplatform.evaluation.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.delta.dms.ops.aidevopsplatform.evaluation.model.ModelEvaluationResult.*;

class ModelEvaluationResultTest {
    @Test void aStubCannotBeReportedAsRealModelQualityPassing() {
        assertThatThrownBy(() -> new ModelEvaluationResult("MQ-JQ01", Mode.STUB, Status.PASS))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void harnessVerificationAndMissingEnvironmentRemainDistinct() {
        assertThat(new ModelEvaluationResult("MQ-JQ01", Mode.STUB, Status.HARNESS_ONLY).status())
                .isEqualTo(Status.HARNESS_ONLY);
        assertThat(new ModelEvaluationResult("MQ-JQ01", Mode.REAL_QWEN, Status.BLOCKED_ENV).status())
                .isEqualTo(Status.BLOCKED_ENV);
    }
}
