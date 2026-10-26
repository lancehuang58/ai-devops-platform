package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;

/** Last-resort VM status through the Glove SSH-tool MCP server. The platform holds no SSH key. */
public interface GloveProbePort {
    ChannelAttempt check(ProbeTarget target);
}
