package com.delta.dms.ops.aidevopsplatform.model.work;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class WorkRequestTest {
    private WorkRequest received() {
        return WorkRequest.receiveQuery(UUID.randomUUID(), UUID.randomUUID(), "N01", "JENKINS-UAT",
                UUID.randomUUID(), Instant.parse("2026-10-21T00:00:00Z"));
    }
    @Test void receiptCapturesAnImmutableFactWithoutRequestContent() {
        var work = received();
        assertThat(work.snapshot().status()).isEqualTo(RequestStatus.RECEIVED);
        assertThat(work.pendingEvents()).singleElement().isInstanceOf(WorkReceived.class);
        var event = work.pendingEvents().getFirst();
        assertThat(event.aggregateId()).isEqualTo(work.snapshot().id());
        assertThat(event.correlationId()).isEqualTo(work.snapshot().correlationId());
        assertThatThrownBy(() -> work.pendingEvents().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void aFaultStopsWorkAndDoesNotPermitRepeatingTheTransition() {
        var work = received();
        work.stop(StopReason.CORE_UNAVAILABLE, UUID.randomUUID(), Instant.now());
        assertThat(work.snapshot().status()).isEqualTo(RequestStatus.PENDING_HUMAN);
        assertThat(work.pendingEvents()).hasSize(2);
        assertThatThrownBy(() -> work.stop(StopReason.TECHNICAL_FAILURE, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(work.pendingEvents()).hasSize(2);
    }
    @Test void invalidStopDoesNotPartiallyChangeState() {
        var work = received();
        assertThatThrownBy(() -> work.stop(StopReason.TECHNICAL_FAILURE, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThat(work.snapshot().status()).isEqualTo(RequestStatus.RECEIVED);
        assertThat(work.pendingEvents()).hasSize(1);
    }
    @Test void missingResourceCannotCreateWork() {
        assertThatThrownBy(() -> WorkRequest.receiveQuery(UUID.randomUUID(), UUID.randomUUID(), "N01", " ",
                UUID.randomUUID(), Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
