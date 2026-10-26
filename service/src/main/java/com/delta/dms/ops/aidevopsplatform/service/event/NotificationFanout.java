package com.delta.dms.ops.aidevopsplatform.service.event;

import com.delta.dms.ops.aidevopsplatform.model.alert.PendingNotice;
import com.delta.dms.ops.aidevopsplatform.service.port.out.TeamsNotificationPort;

import java.util.List;
import java.util.function.Consumer;

/** Delivers one Teams notice per dedup key. A failed send leaves the row pending. */
public final class NotificationFanout {
    public void deliver(List<PendingNotice> pending, TeamsNotificationPort teams, Consumer<String> markSent) {
        for (var notice : pending) {
            teams.notify(notice.dedupKey(), notice.body());
            markSent.accept(notice.dedupKey());
        }
    }
}
