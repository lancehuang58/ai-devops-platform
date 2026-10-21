package com.delta.dms.ops.aidevopsplatform.service.port.out;

/** Re-evaluate query permission for the trusted actor and resource. Fail closed. */
public interface QueryAuthorization {
    void requireAllowed(String actorId, String resourceId);
}
