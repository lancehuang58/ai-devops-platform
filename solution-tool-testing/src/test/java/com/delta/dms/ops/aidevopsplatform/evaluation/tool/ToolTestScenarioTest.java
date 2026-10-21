package com.delta.dms.ops.aidevopsplatform.evaluation.tool;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.delta.dms.ops.aidevopsplatform.evaluation.tool.ToolTestScenario.*;

class ToolTestScenarioTest {
    @Test void productionIsRejectedForEveryTestMode() {
        for (var mode : Mode.values()) {
            assertThatThrownBy(() -> new ToolTestScenario("ST-JQ01", mode, Environment.PRODUCTION))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void realToolTestingRequiresAnIsolatedEnvironment() {
        assertThatThrownBy(() -> new ToolTestScenario("ST-JQ19", Mode.TEST_SERVICE, Environment.LOCAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ToolTestScenario("ST-JQ19", Mode.TEST_SERVICE, Environment.TEST).mode())
                .isEqualTo(Mode.TEST_SERVICE);
    }
}
