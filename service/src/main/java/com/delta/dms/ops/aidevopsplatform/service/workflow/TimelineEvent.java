package com.delta.dms.ops.aidevopsplatform.service.workflow;

import java.time.Instant;

public record TimelineEvent(int sequence, String type, String detail, Instant occurredAt) {
}
