package com.delta.dms.ops.aidevopsplatform.web.integration;

import com.delta.dms.ops.aidevopsplatform.AiDevopsPlatformApplication;
import com.delta.dms.ops.aidevopsplatform.adapter.fixture.InMemoryWorkJournal;
import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.work.RequestStatus;
import com.delta.dms.ops.aidevopsplatform.model.work.WorkReceived;
import com.delta.dms.ops.aidevopsplatform.service.event.NotificationRelay;
import com.delta.dms.ops.aidevopsplatform.service.port.in.DomainEventHandler;
import com.delta.dms.ops.aidevopsplatform.service.port.in.RegisterQueryUseCase;
import com.delta.dms.ops.aidevopsplatform.service.port.out.DomainEventPublisher;
import com.delta.dms.ops.aidevopsplatform.service.port.out.NotificationOutbox;
import com.delta.dms.ops.aidevopsplatform.service.work.RegisterQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = AiDevopsPlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(WorkEventFlowIT.TestWiring.class)
class WorkEventFlowIT {
    @Autowired RegisterQueryUseCase register;
    @Autowired InMemoryWorkJournal journal;
    @Autowired DomainEventPublisher publisher;
    @Autowired ReceiptProjection projection;

    @Test void workCommitAndSpringEventDeliveryUseInterfaces() {
        var receipt = register.register(new RegisterQueryUseCase.Request(UUID.randomUUID(), "N01", "JENKINS-UAT"));
        assertThat(journal.find(receipt.workId()).orElseThrow().status()).isEqualTo(RequestStatus.RECEIVED);
        var event = journal.pending(100).stream().filter(e -> e.aggregateId().equals(receipt.workId())).findFirst().orElseThrow();
        assertThat(projection.seen).doesNotContain(event.eventId());
        new NotificationRelay(journal, publisher).deliverPending(100);
        assertThat(projection.seen).contains(event.eventId());
        assertThat(journal.pending(100)).isEmpty();
    }
    @Test void crashAfterPublicationAllowsReplayWithoutDuplicateProjectionEffect() {
        var receipt = register.register(new RegisterQueryUseCase.Request(UUID.randomUUID(), "N01", "JENKINS-UAT"));
        var event = journal.pending(100).stream().filter(e -> e.aggregateId().equals(receipt.workId())).findFirst().orElseThrow();
        NotificationOutbox crashOnAck = new NotificationOutbox() {
            public List<DomainEvent> pending(int limit) { return List.of(event); }
            public void acknowledge(UUID id) { throw new IllegalStateException("Simulated crash before ack"); }
        };
        assertThatThrownBy(() -> new NotificationRelay(crashOnAck, publisher).deliverPending(1))
                .hasMessage("Simulated crash before ack");
        int effects = projection.seen.size();
        new NotificationRelay(journal, publisher).deliverPending(100);
        assertThat(projection.seen).hasSize(effects);
        assertThat(journal.pending(100)).isEmpty();
    }

    static final class ReceiptProjection implements DomainEventHandler {
        final Set<UUID> seen = new HashSet<>();
        public boolean supports(DomainEvent event) { return event instanceof WorkReceived; }
        public void handle(DomainEvent event) { seen.add(event.eventId()); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TestWiring {
        @Bean InMemoryWorkJournal workJournal() { return new InMemoryWorkJournal(); }
        @Bean ReceiptProjection receiptProjection() { return new ReceiptProjection(); }
        @Bean RegisterQueryUseCase registerQuery(InMemoryWorkJournal store) {
            return new RegisterQueryService((actor, resource) -> {
                if (!"N01".equals(actor) || !"JENKINS-UAT".equals(resource)) throw new SecurityException("Denied");
            }, store, Clock.systemUTC(), UUID::randomUUID);
        }
    }
}
