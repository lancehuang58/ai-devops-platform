package com.delta.dms.ops.aidevopsplatform.adapter.mock;

import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class MockInfrastructureTest {
    private ManagedService service(boolean enabled) {
        return new ManagedService("jenkins-demo","Demo Jenkins",ManagedService.Kind.JENKINS,"TEST",enabled,
                new ManagedService.Connection(ManagedService.Transport.MOCK,java.net.URI.create("https://jenkins.invalid"),null),
                "jenkins-basic-v1",5);
    }
    @Test void mockDirectoryRequiresMatchingCredentials() {
        var directory=new MockLdapDirectory(Map.of("normal","demo-normal"));
        assertThat(directory.authenticate("normal","demo-normal".toCharArray())).isEqualTo("normal");
        assertThatThrownBy(()->directory.authenticate("normal","wrong".toCharArray())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->directory.authenticate("unknown","demo-normal".toCharArray())).isInstanceOf(SecurityException.class);
    }
    @Test void mockObservationAndSummaryAreClearlyMarked() {
        var observation=new MockJenkinsProbe(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC)).observe(service(true));
        var summary=new MockLlmSummaryAdapter("selected-model").summarize(service(true),observation);
        assertThat(observation.mocked()).isTrue();assertThat(observation.observedAt()).isEqualTo(Instant.EPOCH);
        assertThat(summary.text()).contains("[MOCK]","jenkins-demo","UP");
        assertThat(summary.modelName()).isEqualTo("selected-model");assertThat(summary.mocked()).isTrue();
    }
}
