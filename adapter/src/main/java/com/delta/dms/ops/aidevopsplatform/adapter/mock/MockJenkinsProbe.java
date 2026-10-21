package com.delta.dms.ops.aidevopsplatform.adapter.mock;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.Observation;
import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ServiceProbePort;
import java.time.Clock;

public final class MockJenkinsProbe implements ServiceProbePort {
    private final Clock clock;
    public MockJenkinsProbe(Clock clock) { this.clock=clock; }
    @Override public Observation observe(ManagedService service) {
        if(service.kind() != ManagedService.Kind.JENKINS || service.connection().transport() != ManagedService.Transport.MOCK
                || !"jenkins-basic-v1".equals(service.healthProfile()) || !service.enabled())
            throw new IllegalArgumentException("Unsupported mock probe binding");
        return new Observation(Observation.Status.UP,"mock-jenkins-1",clock.instant(),true);
    }
}
