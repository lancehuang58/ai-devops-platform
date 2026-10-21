package com.delta.dms.ops.aidevopsplatform.evaluation.tool;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.Observation;
import com.delta.dms.ops.aidevopsplatform.service.port.in.ManagedServiceQueryUseCase;

/** Uses the application use case, not a second copy of its workflow. */
public final class ManagedServiceScenarioRunner implements SolutionToolRunner {
    private final ManagedServiceQueryUseCase query;
    private final String actorId;
    private final String serviceId;
    public ManagedServiceScenarioRunner(ManagedServiceQueryUseCase query, String actorId, String serviceId) {
        this.query=query; this.actorId=actorId; this.serviceId=serviceId;
    }
    @Override public void run(ToolTestScenario scenario) {
        if(!"ST-DEMO-001".equals(scenario.caseId()) || scenario.mode()!=ToolTestScenario.Mode.OFFLINE)
            throw new IllegalArgumentException("Only the offline demo scenario is implemented");
        var report=query.inspect(actorId,serviceId);
        if(!report.serviceId().equals(serviceId) || report.status()!=Observation.Status.UP
                || !report.probeMocked() || !report.modelMocked())
            throw new AssertionError("Offline managed-service scenario did not match expected mock observation");
        if(!query.report(actorId,report.id()).equals(report)) throw new AssertionError("Stored report differs");
    }
}
