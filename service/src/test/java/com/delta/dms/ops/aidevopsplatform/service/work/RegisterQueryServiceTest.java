package com.delta.dms.ops.aidevopsplatform.service.work;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.work.WorkSnapshot;
import com.delta.dms.ops.aidevopsplatform.service.port.in.RegisterQueryUseCase;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkCommitPort;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RegisterQueryServiceTest {
    private final Instant now = Instant.parse("2026-10-21T00:00:00Z");
    private RegisterQueryUseCase.Request request() {
        return new RegisterQueryUseCase.Request(UUID.randomUUID(), "N01", "JENKINS-UAT");
    }
    @Test void authorizationPrecedesAtomicWorkAndEventCommit() {
        var calls = new ArrayList<String>();
        WorkCommitPort store = (work, events) -> {
            calls.add("commit");
            assertThat(events).hasSize(1);
            assertThat(events.getFirst().aggregateId()).isEqualTo(work.id());
            assertThat(events.getFirst().occurredAt()).isEqualTo(now);
        };
        RegisterQueryUseCase service = new RegisterQueryService((actor, resource) -> calls.add("authorize"),
                store, Clock.fixed(now, ZoneOffset.UTC), UUID::randomUUID);
        var request = request();
        assertThat(service.register(request).correlationId()).isEqualTo(request.correlationId());
        assertThat(calls).containsExactly("authorize", "commit");
    }
    @Test void deniedQueryHasNoPersistenceSideEffect() {
        var service = new RegisterQueryService((actor, resource) -> { throw new SecurityException("Denied"); },
                (work, events) -> fail("Must not save a denied request"), Clock.systemUTC(), UUID::randomUUID);
        assertThatThrownBy(() -> service.register(request())).isInstanceOf(SecurityException.class);
    }
    @Test void failedCommitDoesNotReturnAReceipt() {
        var service = new RegisterQueryService((actor, resource) -> { },
                (work, events) -> { throw new IllegalStateException("Database unavailable"); },
                Clock.systemUTC(), UUID::randomUUID);
        assertThatThrownBy(() -> service.register(request())).hasMessage("Database unavailable");
    }
    @Test void malformedRequestNeverReachesAuthorizationOrStorage() {
        var service = new RegisterQueryService((actor, resource) -> fail("Must validate first"),
                (work, events) -> fail("Must not save"), Clock.systemUTC(), UUID::randomUUID);
        assertThatThrownBy(() -> service.register(new RegisterQueryUseCase.Request(UUID.randomUUID(), "", "R1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
