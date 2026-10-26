package com.delta.dms.ops.aidevopsplatform.model.sync;

import java.util.UUID;

/** A claimed Jira outbox row. The platform sends it; it does not decide the workflow. */
public record DueSync(UUID requestId, String itemNo, String summary) {
}
