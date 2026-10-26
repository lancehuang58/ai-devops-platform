package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;

public interface ApiProbePort {
    ChannelAttempt check(ProbeTarget target);
}
