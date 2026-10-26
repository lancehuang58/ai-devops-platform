package com.delta.dms.ops.aidevopsplatform.adapter.probe;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelVerdict;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ApiProbePort;

public final class UnavailableApiProbe implements ApiProbePort {
    @Override public ChannelAttempt check(ProbeTarget target) {
        return new ChannelAttempt(ProbeChannel.API, ChannelVerdict.CHANNEL_FAILED, "api probe is not configured");
    }
}
