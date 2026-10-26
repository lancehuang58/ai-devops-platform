package com.delta.dms.ops.aidevopsplatform.adapter.probe;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelVerdict;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.service.port.out.McpProbePort;

public final class UnavailableMcpProbe implements McpProbePort {
    @Override public ChannelAttempt check(ProbeTarget target) {
        return new ChannelAttempt(ProbeChannel.MCP, ChannelVerdict.CHANNEL_FAILED, "mcp server unavailable");
    }
}
