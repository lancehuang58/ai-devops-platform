package com.delta.dms.ops.aidevopsplatform.model.work;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Initial query lifecycle only. No mutation execution is implemented here. */
public final class WorkRequest {
    private WorkSnapshot state;
    private final List<DomainEvent> events = new ArrayList<>();

    private WorkRequest(WorkSnapshot state) { this.state = state; }

    public static WorkRequest receiveQuery(UUID id, UUID correlationId, String actorId,
                                           String resourceId, UUID eventId, Instant now) {
        var work = new WorkRequest(new WorkSnapshot(id, correlationId, actorId,
                resourceId, RequestStatus.RECEIVED));
        work.events.add(new WorkReceived(eventId, id, correlationId, now));
        return work;
    }

    public void stop(StopReason reason, UUID eventId, Instant now) {
        Objects.requireNonNull(reason);
        if (state.status() != RequestStatus.RECEIVED) {
            throw new IllegalStateException("Stopped work cannot restart or stop again");
        }
        var event = new WorkStopped(eventId, state.id(), state.correlationId(), now, reason);
        state = new WorkSnapshot(state.id(), state.correlationId(), state.actorId(),
                state.resourceId(), RequestStatus.PENDING_HUMAN);
        events.add(event);
    }

    public WorkSnapshot snapshot() { return state; }
    public List<DomainEvent> pendingEvents() { return List.copyOf(events); }
}
