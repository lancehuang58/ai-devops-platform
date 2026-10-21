package com.delta.dms.ops.aidevopsplatform.evaluation.model;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.Observation;
import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ServiceSummaryPort;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
class ServiceSummaryEvaluatorTest {
    private ManagedService service(boolean enabled) {
        return new ManagedService("jenkins-demo","Demo Jenkins",ManagedService.Kind.JENKINS,"TEST",enabled,
                new ManagedService.Connection(ManagedService.Transport.MOCK,java.net.URI.create("https://jenkins.invalid"),null),
                "jenkins-basic-v1",5);
    }
    @Test void aMatchingStubIsHarnessOnlyAndIncorrectTextFails() {
        var observation=new Observation(Observation.Status.DOWN,"v1",Instant.EPOCH,true);
        var good=new ServiceSummaryEvaluator((s,o)->new ServiceSummaryPort.Summary("[MOCK] jenkins-demo DOWN","stub",true),service(true),observation);
        var wrong=new ServiceSummaryEvaluator((s,o)->new ServiceSummaryPort.Summary("[MOCK] jenkins-demo UP","stub",true),service(true),observation);
        assertThat(good.evaluate("MQ-DEMO-001").status()).isEqualTo(ModelEvaluationResult.Status.HARNESS_ONLY);
        assertThat(wrong.evaluate("MQ-DEMO-001").status()).isEqualTo(ModelEvaluationResult.Status.FAIL);
    }
}
