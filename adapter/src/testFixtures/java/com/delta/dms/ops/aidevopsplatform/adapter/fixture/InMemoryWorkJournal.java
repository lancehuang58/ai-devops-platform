package com.delta.dms.ops.aidevopsplatform.adapter.fixture;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.work.WorkSnapshot;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkCommitPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.NotificationOutbox;
import java.util.*;

/** Test fixture only. Not durable and never included in the production application. */
public final class InMemoryWorkJournal implements WorkCommitPort, NotificationOutbox {
    private final Map<UUID, WorkSnapshot> works = new HashMap<>();
    private final Map<UUID, DomainEvent> pending = new LinkedHashMap<>();
    private final Set<UUID> knownEvents = new HashSet<>();

    @Override public synchronized void create(WorkSnapshot work, List<DomainEvent> events) {
        Objects.requireNonNull(work);
        var copy = List.copyOf(events);
        if (works.containsKey(work.id())) throw new IllegalStateException("Duplicate work");
        var newIds = new HashSet<UUID>();
        for (var event : copy) {
            if (!event.aggregateId().equals(work.id()) || !event.correlationId().equals(work.correlationId())
                    || knownEvents.contains(event.eventId()) || !newIds.add(event.eventId())) {
                throw new IllegalArgumentException("Invalid or duplicate event");
            }
        }
        works.put(work.id(), work);
        for (var event : copy) pending.put(event.eventId(), event);
        knownEvents.addAll(newIds);
    }
    @Override public synchronized List<DomainEvent> pending(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Invalid batch size");
        return pending.values().stream().limit(limit).toList();
    }
    @Override public synchronized void acknowledge(UUID eventId) { pending.remove(eventId); }
    public synchronized Optional<WorkSnapshot> find(UUID id) { return Optional.ofNullable(works.get(id)); }
    public synchronized int size() { return works.size(); }
}
