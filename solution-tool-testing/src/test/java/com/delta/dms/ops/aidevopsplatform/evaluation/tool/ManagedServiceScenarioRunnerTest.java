package com.delta.dms.ops.aidevopsplatform.evaluation.tool;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ManagedServiceScenarioRunnerTest {
    @Test void realServiceModeIsNotSilentlyRunAsOffline() {
        var runner=new ManagedServiceScenarioRunner(null,"normal","jenkins-demo");
        assertThatThrownBy(()->runner.run(new ToolTestScenario("ST-DEMO-001",ToolTestScenario.Mode.TEST_SERVICE,ToolTestScenario.Environment.TEST)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("offline");
    }
}
