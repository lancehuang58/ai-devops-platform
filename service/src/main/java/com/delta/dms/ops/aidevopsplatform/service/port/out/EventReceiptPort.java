package com.delta.dms.ops.aidevopsplatform.service.port.out;

import java.util.UUID;
public interface EventReceiptPort { void recordOnce(UUID eventId); }
