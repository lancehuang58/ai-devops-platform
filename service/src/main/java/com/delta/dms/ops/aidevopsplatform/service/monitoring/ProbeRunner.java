package com.delta.dms.ops.aidevopsplatform.service.monitoring;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelVerdict;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChain;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeDecision;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ApiProbePort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.EndpointProbePort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.GloveProbePort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.McpProbePort;

import java.util.ArrayList;

public final class ProbeRunner {
    public ProbeDecision check(ProbeTarget target, EndpointProbePort endpoint, McpProbePort mcp,
                               ApiProbePort api, GloveProbePort glove) {
        var attempts = new ArrayList<ChannelAttempt>();
        for (var channel : ProbeChain.order(target.channels())) {
            attempts.add(switch (channel) {
                case ENDPOINT -> endpoint.check(target);
                case MCP -> mcp.check(target);
                case API -> api.check(target);
                case VM -> glove.check(target);
            });
            if (attempts.getLast().verdict() != ChannelVerdict.CHANNEL_FAILED) break;
        }
        return ProbeChain.decide(target.instanceId(), attempts);
    }
}
