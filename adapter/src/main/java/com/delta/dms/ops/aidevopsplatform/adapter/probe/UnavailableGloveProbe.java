package com.delta.dms.ops.aidevopsplatform.adapter.probe;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelVerdict;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.service.port.out.GloveProbePort;

/** Glove is the SSH-tool MCP server. This stand-in does not open a connection. */
public final class UnavailableGloveProbe implements GloveProbePort {
    @Override public ChannelAttempt check(ProbeTarget target) {
        return new ChannelAttempt(ProbeChannel.VM, ChannelVerdict.CHANNEL_FAILED, "glove mcp server unavailable");
    }
}
