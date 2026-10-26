package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.util.List;

public record ResourceIdentity(
        String resourceId,
        String resourceType,
        String environment,
        boolean resolved,
        List<String> serviceRefs) {
}
