package com.delta.dms.ops.aidevopsplatform.service.port.in;

import java.util.UUID;

public interface RegisterQueryUseCase {
    Receipt register(Request request);
    /** actorId is resolved by a trusted ingress, never from a public request body. */
    record Request(UUID correlationId, String actorId, String resourceId) {}
    record Receipt(UUID workId, UUID correlationId) {}
}
