package com.delta.dms.ops.aidevopsplatform.adapter.probe;

import com.delta.dms.ops.aidevopsplatform.adapter.alert.AlertIntake;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.model.alert.GenericAlert;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeDisposition;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbePublication;

import java.util.Map;

/** Writes probe attempts, then either opens the canonical alert pipeline or a deduped channel notice. */
public final class ProbePublisher {
    private final JdbcPlatformStore store;
    private final AlertIntake intake;

    public ProbePublisher(JdbcPlatformStore store, AlertIntake intake) {
        this.store = store;
        this.intake = intake;
    }

    public void publish(ProbePublication publication) {
        var decision = publication.decision();
        store.saveProbeResults(publication.target().instanceId(), decision.attempts(), decision.disposition().name(),
                publication.observedAt());
        if (decision.disposition() == ProbeDisposition.HEALTHY) return;
        if (decision.disposition() == ProbeDisposition.CHANNEL_DEGRADED) {
            store.enqueueNoticeOnce(decision.dedupKey(), "CHANNEL_DEGRADED", decision.summary(), publication.observedAt());
            return;
        }
        var result = intake.generic(alert(publication), publication.observedAt());
        if (result.itemId() != null) store.recordProbeTrail(result.itemId(), decision.summary(), publication.observedAt());
    }

    private static GenericAlert alert(ProbePublication publication) {
        var target = publication.target();
        var decision = publication.decision();
        var code = decision.producingChannel() == null ? "ALL_CHANNELS" : decision.producingChannel().name();
        return new GenericAlert("probe", target.instanceId(), target.instanceId(), target.serviceType(),
                target.environment(), decision.symptomType(), code, Map.of(), "major", "firing",
                publication.observedAt(), decision.summary());
    }
}
