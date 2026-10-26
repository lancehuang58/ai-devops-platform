package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;

public interface EndpointProbePort {
    ChannelAttempt check(ProbeTarget target);
}
