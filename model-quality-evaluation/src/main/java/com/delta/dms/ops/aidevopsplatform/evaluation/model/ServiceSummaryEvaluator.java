package com.delta.dms.ops.aidevopsplatform.evaluation.model;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.Observation;
import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ServiceSummaryPort;

/** Executes the real summary port with a fixed synthetic observation; no gold is sent to it. */
public final class ServiceSummaryEvaluator implements ModelQualityEvaluator {
    private final ServiceSummaryPort summaryPort;
    private final ManagedService service;
    private final Observation observation;
    public ServiceSummaryEvaluator(ServiceSummaryPort summaryPort, ManagedService service, Observation observation) {
        this.summaryPort=summaryPort; this.service=service; this.observation=observation;
    }
    @Override public ModelEvaluationResult evaluate(String caseId) {
        if(!"MQ-DEMO-001".equals(caseId)) throw new IllegalArgumentException("Unknown demo MQ case");
        var result=summaryPort.summarize(service,observation);
        if(!result.mocked()) throw new IllegalStateException("This harness is for a mock model only");
        boolean valid=result.text()!=null && result.text().contains("[MOCK]")
                && result.text().contains(service.id()) && result.text().contains(observation.status().name());
        return new ModelEvaluationResult(caseId,ModelEvaluationResult.Mode.STUB,
                valid ? ModelEvaluationResult.Status.HARNESS_ONLY : ModelEvaluationResult.Status.FAIL);
    }
}
