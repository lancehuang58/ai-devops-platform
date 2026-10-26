package com.delta.dms.ops.aidevopsplatform.adapter.llm;

import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;
import com.delta.dms.ops.aidevopsplatform.service.port.out.LlmPort;

/** Fake Qwen/LiteLLM port. v1 never calls a model endpoint. */
public final class UnavailableLlm implements LlmPort {
    @Override public boolean available() { return false; }
    @Override public String draftProposal(WorkItemSnapshot item) { return ""; }
}
