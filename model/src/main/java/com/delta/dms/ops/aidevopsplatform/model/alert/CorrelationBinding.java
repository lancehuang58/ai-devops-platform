package com.delta.dms.ops.aidevopsplatform.model.alert;

public record CorrelationBinding(String ruleId, String environment, String upstreamResourceId, String resourceId) {
    public String activeKey() {
        return ruleId + ":" + environment + ":" + upstreamResourceId;
    }
}
