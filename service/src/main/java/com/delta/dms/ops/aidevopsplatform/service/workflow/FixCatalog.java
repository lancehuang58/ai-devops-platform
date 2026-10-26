package com.delta.dms.ops.aidevopsplatform.service.workflow;

import com.delta.dms.ops.aidevopsplatform.model.remediation.AutoRemediationRule;
import com.delta.dms.ops.aidevopsplatform.model.remediation.PublishedSolution;
import com.delta.dms.ops.aidevopsplatform.model.remediation.RemediationRun;

import java.util.List;

public record FixCatalog(List<PublishedSolution> solutions, List<AutoRemediationRule> rules, List<RemediationRun> runs) {
    public FixCatalog {
        solutions = List.copyOf(solutions);
        rules = List.copyOf(rules);
        runs = List.copyOf(runs);
    }
}
