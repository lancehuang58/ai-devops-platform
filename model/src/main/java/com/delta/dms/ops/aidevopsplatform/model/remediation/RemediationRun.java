package com.delta.dms.ops.aidevopsplatform.model.remediation;

import java.time.Instant;

public record RemediationRun(String fingerprint, String resourceId, Instant ranAt) {
}
