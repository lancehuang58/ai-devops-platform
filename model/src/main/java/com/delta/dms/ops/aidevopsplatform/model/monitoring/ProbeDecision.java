package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import java.util.List;

public record ProbeDecision(
        ProbeDisposition disposition,
        String symptomType,
        String summary,
        String dedupKey,
        List<ChannelAttempt> attempts,
        ProbeChannel producingChannel) {
}
