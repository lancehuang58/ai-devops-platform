package com.delta.dms.ops.aidevopsplatform.model.takeover;

import java.util.Map;

public record MaskedText(String text, Map<String, String> aliases) {
}
