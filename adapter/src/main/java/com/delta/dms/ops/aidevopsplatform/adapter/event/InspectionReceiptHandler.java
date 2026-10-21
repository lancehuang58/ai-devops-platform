package com.delta.dms.ops.aidevopsplatform.adapter.event;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ServiceInspected;
import com.delta.dms.ops.aidevopsplatform.service.port.in.DomainEventHandler;
import com.delta.dms.ops.aidevopsplatform.service.port.out.EventReceiptPort;

public final class InspectionReceiptHandler implements DomainEventHandler {
    private final EventReceiptPort receipts;
    public InspectionReceiptHandler(EventReceiptPort receipts) { this.receipts=receipts; }
    @Override public boolean supports(DomainEvent event) { return event instanceof ServiceInspected; }
    @Override public void handle(DomainEvent event) { receipts.recordOnce(event.eventId()); }
}
