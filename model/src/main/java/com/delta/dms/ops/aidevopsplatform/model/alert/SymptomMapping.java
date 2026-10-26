package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.util.List;

public record SymptomMapping(String symptomType, List<String> discriminatorKeys) {
}
