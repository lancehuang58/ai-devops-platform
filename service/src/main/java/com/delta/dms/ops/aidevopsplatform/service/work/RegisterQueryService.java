package com.delta.dms.ops.aidevopsplatform.service.work;

import com.delta.dms.ops.aidevopsplatform.model.work.WorkRequest;
import com.delta.dms.ops.aidevopsplatform.service.port.in.RegisterQueryUseCase;
import com.delta.dms.ops.aidevopsplatform.service.port.out.QueryAuthorization;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkCommitPort;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class RegisterQueryService implements RegisterQueryUseCase {
    private final QueryAuthorization authorization;
    private final WorkCommitPort store;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public RegisterQueryService(QueryAuthorization authorization, WorkCommitPort store,
                                Clock clock, Supplier<UUID> ids) {
        this.authorization = Objects.requireNonNull(authorization);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Receipt register(Request request) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(request.correlationId());
        if (request.actorId() == null || request.actorId().isBlank()
                || request.resourceId() == null || request.resourceId().isBlank()) {
            throw new IllegalArgumentException("Actor and resource references are required");
        }
        authorization.requireAllowed(request.actorId(), request.resourceId());
        var work = WorkRequest.receiveQuery(ids.get(), request.correlationId(),
                request.actorId(), request.resourceId(), ids.get(), clock.instant());
        store.create(work.snapshot(), work.pendingEvents());
        return new Receipt(work.snapshot().id(), request.correlationId());
    }
}
