package com.delta.dms.ops.aidevopsplatform.adapter.probe;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelVerdict;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.service.port.out.EndpointProbePort;

public final class UnavailableEndpointProbe implements EndpointProbePort {
    @Override public ChannelAttempt check(ProbeTarget target) {
        return new ChannelAttempt(ProbeChannel.ENDPOINT, ChannelVerdict.CHANNEL_FAILED, "endpoint probe is not configured");
    }
}
