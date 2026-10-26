package com.delta.dms.ops.aidevopsplatform.service.port.out;

public interface TeamsNotificationPort {
    void notify(String dedupKey, String body);
}
