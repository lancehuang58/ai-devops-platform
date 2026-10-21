package com.delta.dms.ops.aidevopsplatform.service.monitoring;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.*;
import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.out.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class ManagedServiceQueryServiceTest {
    private ManagedService service(boolean enabled) {
        return new ManagedService("jenkins-demo","Demo Jenkins",ManagedService.Kind.JENKINS,"TEST",enabled,
                new ManagedService.Connection(ManagedService.Transport.MOCK,java.net.URI.create("https://jenkins.invalid"),null),
                "jenkins-basic-v1",5);
    }
    private final List<String> calls=new ArrayList<>();
    private final Map<UUID,InspectionReport> saved=new HashMap<>();
    private ManagedServiceQueryService useCase(String failure) {
        var services=new ManagedServiceRepository() {
            public List<ManagedService> findAll() {return List.of(service(!"disabled".equals(failure)));}
            public Optional<ManagedService> findById(String id) {return findAll().stream().filter(s->s.id().equals(id)).findFirst();}
        };
        var reports=new InspectionRepository() {
            public void save(InspectionReport report,ServiceInspected event) {
                hit("commit",failure);assertThat(event.aggregateId()).isEqualTo(report.id());saved.put(report.id(),report);
            }
            public Optional<InspectionReport> findById(UUID id) {return Optional.ofNullable(saved.get(id));}
        };
        return new ManagedServiceQueryService((a,r)->hit("authorize",failure),services,
                s->{hit("probe",failure);return new Observation(Observation.Status.UP,"mock",Instant.EPOCH,true);},
                (s,o)->{hit("model",failure);return new ServiceSummaryPort.Summary("[MOCK] UP","mock",true);},
                reports,limit->{hit("notify",failure);return 1;});
    }
    private void hit(String step,String failure) {calls.add(step);if(step.equals(failure))throw new IllegalStateException(step);}
    @Test void fixedQueryOrdersAuthorizationObservationModelCommitAndNotification() {
        var query=useCase("");var report=query.inspect("normal","jenkins-demo");
        assertThat(calls).containsExactly("authorize","probe","model","commit","notify");
        assertThat(query.report("normal",report.id())).isEqualTo(report);
        assertThat(calls.stream().filter("probe"::equals).count()).isEqualTo(1);
    }
    @Test void deniedAccessHasNoDownstreamSideEffects() {
        assertThatThrownBy(()->useCase("authorize").inspect("none","jenkins-demo")).hasMessage("authorize");
        assertThat(calls).containsExactly("authorize");
    }
    @Test void disabledServiceIsNotProbed() {
        assertThatThrownBy(()->useCase("disabled").inspect("normal","jenkins-demo")).isInstanceOf(NoSuchElementException.class);
        assertThat(calls).containsExactly("authorize");
    }
    @Test void probeFailureStopsBeforeCallingTheModel() {
        assertThatThrownBy(()->useCase("probe").inspect("normal","jenkins-demo")).hasMessage("probe");
        assertThat(calls).containsExactly("authorize","probe");
    }
    @Test void modelFailureDoesNotCommitAnInventedReport() {
        assertThatThrownBy(()->useCase("model").inspect("normal","jenkins-demo")).hasMessage("model");
        assertThat(calls).containsExactly("authorize","probe","model");assertThat(saved).isEmpty();
    }
    @Test void transactionFailureDoesNotPublish() {
        assertThatThrownBy(()->useCase("commit").inspect("normal","jenkins-demo")).hasMessage("commit");
        assertThat(calls).doesNotContain("notify");assertThat(saved).isEmpty();
    }
    @Test void notificationFailurePreservesCommittedReportWithoutRetryingProbe() {
        assertThatThrownBy(()->useCase("notify").inspect("normal","jenkins-demo")).hasMessage("notify");
        assertThat(saved).hasSize(1);assertThat(calls).containsExactly("authorize","probe","model","commit","notify");
    }
}
