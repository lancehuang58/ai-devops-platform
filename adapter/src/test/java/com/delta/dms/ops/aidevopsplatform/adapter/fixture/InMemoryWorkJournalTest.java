package com.delta.dms.ops.aidevopsplatform.adapter.fixture;

import com.delta.dms.ops.aidevopsplatform.model.work.WorkRequest;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class InMemoryWorkJournalTest {
    @Test void duplicateEventsDoNotPartiallyPersistWork() {
        var store = new InMemoryWorkJournal();
        var work = WorkRequest.receiveQuery(UUID.randomUUID(), UUID.randomUUID(), "N01", "R1", UUID.randomUUID(), Instant.now());
        var event = work.pendingEvents().getFirst();
        assertThatThrownBy(() -> store.create(work.snapshot(), List.of(event, event))).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.size()).isZero();
        assertThat(store.pending(10)).isEmpty();
    }
    @Test void duplicateWorkCannotCreateAnotherNotification() {
        var store = new InMemoryWorkJournal();
        var work = WorkRequest.receiveQuery(UUID.randomUUID(), UUID.randomUUID(), "N01", "R1", UUID.randomUUID(), Instant.now());
        store.create(work.snapshot(), work.pendingEvents());
        assertThatThrownBy(() -> store.create(work.snapshot(), work.pendingEvents())).isInstanceOf(IllegalStateException.class);
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.pending(10)).hasSize(1);
    }
}
