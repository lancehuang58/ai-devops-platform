package com.delta.dms.ops.aidevopsplatform.adapter.mock;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.Observation;
import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ServiceSummaryPort;

public final class MockLlmSummaryAdapter implements ServiceSummaryPort {
    private final String modelName;
    public MockLlmSummaryAdapter(String modelName) {
        if(modelName == null || modelName.isBlank()) throw new IllegalArgumentException("Model name required");
        this.modelName=modelName;
    }
    @Override public Summary summarize(ManagedService service, Observation observation) {
        return new Summary("[MOCK] " + service.id() + " 狀態：" + observation.status()
                + "；版本：" + observation.version() + "。此為合成觀測，未連線實際 Jenkins 或 Qwen。",modelName,true);
    }
}
