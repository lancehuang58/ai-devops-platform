package com.delta.dms.ops.aidevopsplatform.web.integration;

import com.delta.dms.ops.aidevopsplatform.adapter.persistence.H2DemoInspectionStore;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class H2InspectionTransactionIT {
    private H2DemoInspectionStore store() {return new H2DemoInspectionStore("jdbc:h2:mem:test_"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1","sa","");}
    private InspectionReport report() {return new InspectionReport(UUID.randomUUID(),UUID.randomUUID(),"normal","jenkins-demo","Demo",
            Observation.Status.UP,"mock",Instant.parse("2026-10-21T00:00:00.123456789Z"),"[MOCK] UP","mock",true,true);}
    private ServiceInspected event(InspectionReport report,UUID id) {return new ServiceInspected(id,report.id(),report.correlationId(),report.observedAt());}
    @Test void outboxInsertFailureRollsBackTheReportInsert() {
        var store=store();var first=report();var eventId=UUID.randomUUID();store.save(first,event(first,eventId));
        var second=report();
        assertThatThrownBy(()->store.save(second,event(second,eventId))).isInstanceOf(IllegalStateException.class);
        assertThat(store.findById(second.id())).isEmpty();assertThat(store.pending(10)).hasSize(1);
        assertThat(store.findById(first.id())).contains(first);
        assertThat(store.pending(10)).containsExactly(event(first,eventId));
    }
    @Test void requiredConsumerReceiptPrecedesAckAndDuplicateReceiptsAreSafe() {
        var store=store();var report=report();var id=UUID.randomUUID();store.save(report,event(report,id));
        assertThatThrownBy(()->store.acknowledge(id)).hasMessage("Required consumer has not received event");
        store.recordOnce(id);store.recordOnce(id);store.acknowledge(id);
        assertThat(store.pending(10)).isEmpty();assertThat(store.findById(report.id())).contains(report);
    }
}
