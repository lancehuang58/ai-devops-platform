package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import java.util.UUID;

public record TaskClaim(UUID taskId, UUID itemId) {
}
