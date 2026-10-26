package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

public record CorrelationBinding(String ruleId, String environment, String upstreamResourceId, String resourceId) {
    public String activeKey() {
        return ruleId + ":" + environment + ":" + upstreamResourceId;
    }
}
