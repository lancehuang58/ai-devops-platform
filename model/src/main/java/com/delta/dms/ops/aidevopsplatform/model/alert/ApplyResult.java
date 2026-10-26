package com.delta.dms.ops.aidevopsplatform.model.alert;

import java.util.List;

public record ApplyResult(GroupEffect effect, List<String> notifications, boolean lateRefire) {
}
