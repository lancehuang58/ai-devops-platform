package com.delta.dms.ops.aidevopsplatform.model.catalog;

import java.util.Map;

public record IntegratedService(
        String id,
        String type,
        String baseUrl,
        Map<String, String> secrets,
        Map<String, String> settings) {
}
