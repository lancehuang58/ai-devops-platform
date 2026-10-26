package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import java.util.UUID;

public record IngestResult(boolean duplicate, boolean openedCase, UUID groupId, UUID itemId, UUID candidateId) {
    static IngestResult duplicate(UUID groupId) {
        return new IngestResult(true, false, groupId, null, null);
    }

    static IngestResult opened(UUID groupId, UUID itemId) {
        return new IngestResult(false, true, groupId, itemId, null);
    }

    static IngestResult counted(UUID groupId, UUID itemId) {
        return new IngestResult(false, false, groupId, itemId, null);
    }

    static IngestResult candidate(UUID groupId, UUID candidateId) {
        return new IngestResult(false, false, groupId, null, candidateId);
    }
}
