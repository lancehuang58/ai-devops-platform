package com.delta.dms.ops.aidevopsplatform.adapter.notify;

import com.delta.dms.ops.aidevopsplatform.service.port.out.TeamsNotificationPort;

/** In-process Teams stand-in. The body stays in the outbox; this port does not call Graph. */
public final class LoggingTeamsNotifier implements TeamsNotificationPort {
    @Override public void notify(String dedupKey, String body) { }
}
