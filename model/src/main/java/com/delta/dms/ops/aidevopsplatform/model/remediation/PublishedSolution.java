package com.delta.dms.ops.aidevopsplatform.model.remediation;

public record PublishedSolution(String solutionId, String version, String symptomType, boolean published) {
    public String ref() { return solutionId + "@" + version; }
}
