package com.delta.dms.ops.aidevopsplatform.model.monitoring;

public record ChannelAttempt(ProbeChannel channel, ChannelVerdict verdict, String detail) {
}
