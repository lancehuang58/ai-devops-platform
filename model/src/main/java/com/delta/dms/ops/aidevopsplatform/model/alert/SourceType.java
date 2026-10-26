package com.delta.dms.ops.aidevopsplatform.model.alert;

public enum SourceType {
    PROMETHEUS_ALERTMANAGER,
    PROMETHEUS_QUERY,
    HTTP_ENDPOINT_PROBE,
    PLATFORM_HEALTH_PROFILE,
    CUSTOM_CONNECTOR,
    USER_REPORT
}
