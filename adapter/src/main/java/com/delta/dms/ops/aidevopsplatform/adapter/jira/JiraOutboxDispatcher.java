package com.delta.dms.ops.aidevopsplatform.adapter.jira;

import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.service.port.out.JiraPort;

import java.time.Instant;

/** Claims due Jira rows and stops after the sync policy's fifth retry. */
public final class JiraOutboxDispatcher {
    private final JdbcPlatformStore store;
    private final JiraPort jira;

    public JiraOutboxDispatcher(JdbcPlatformStore store, JiraPort jira) {
        this.store = store;
        this.jira = jira;
    }

    public int dispatchDue(Instant now, int limit) {
        var due = store.claimDueSyncs(now, limit);
        for (var request : due) {
            try {
                if (jira.createIssue(request.itemNo(), request.summary())) store.succeedSync(request.requestId(), now);
                else store.failSync(request.requestId(), now, 0.5);
            } catch (RuntimeException failure) {
                store.failSync(request.requestId(), now, 0.5);
            }
        }
        return due.size();
    }
}
