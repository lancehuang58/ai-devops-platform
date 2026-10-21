package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class InspectionReportTest {
    private ManagedService service(boolean enabled) {
        return new ManagedService("jenkins-demo","Demo Jenkins",ManagedService.Kind.JENKINS,"TEST",enabled,
                new ManagedService.Connection(ManagedService.Transport.MOCK,java.net.URI.create("https://jenkins.invalid"),null),
                "jenkins-basic-v1",5);
    }
    @Test void statusComesFromObservationAndNotSummary() {
        var r=InspectionReport.complete(UUID.randomUUID(),UUID.randomUUID(),"normal",service(true),
                new Observation(Observation.Status.DOWN,"v1",Instant.now(),true),"model says UP","mock",true);
        assertThat(r.status()).isEqualTo(Observation.Status.DOWN);
    }
    @Test void disabledServiceCannotProduceACompletedReport() {
        assertThatThrownBy(() -> InspectionReport.complete(UUID.randomUUID(),UUID.randomUUID(),"normal",service(false),
                new Observation(Observation.Status.UP,"v1",Instant.now(),true),"summary","mock",true))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void credentialsCannotBeEmbeddedInManagedEndpoints() {
        assertThatThrownBy(() -> new ManagedService.Connection(ManagedService.Transport.MCP,
                java.net.URI.create("https://user:secret@jenkins.invalid"),null)).isInstanceOf(IllegalArgumentException.class);
    }
}
