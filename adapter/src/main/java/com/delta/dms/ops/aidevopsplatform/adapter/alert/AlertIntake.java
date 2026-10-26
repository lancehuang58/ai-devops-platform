package com.delta.dms.ops.aidevopsplatform.adapter.alert;

import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.model.alert.AlertNormalizer;
import com.delta.dms.ops.aidevopsplatform.model.alert.AlertmanagerAlert;
import com.delta.dms.ops.aidevopsplatform.model.alert.GenericAlert;
import com.delta.dms.ops.aidevopsplatform.model.alert.IngestResult;

import java.time.Instant;
import java.util.UUID;

public final class AlertIntake {
    private final JdbcPlatformStore store;
    private final AlertNormalizer normalizer = new AlertNormalizer();
    private final AlertFieldMapper fields = new AlertFieldMapper();

    public AlertIntake(JdbcPlatformStore store) { this.store = store; }

    public IngestResult alertmanager(String sourceId, String version, AlertmanagerAlert alert, Instant receivedAt) {
        normalizer.requireAlertmanagerVersion(version);
        var labels = alert.labels() == null ? java.util.Map.<String, String>of() : alert.labels();
        var canonical = normalizer.alertmanager(sourceId, alert, fields.symptom(labels.get("alertname")),
                fields.resource(labels), receivedAt, UUID.randomUUID(), UUID.randomUUID(), "");
        return store.ingest(canonical, receivedAt, null);
    }

    public IngestResult generic(GenericAlert alert, Instant receivedAt) {
        var canonical = normalizer.generic(alert, receivedAt, UUID.randomUUID(), UUID.randomUUID(), "");
        return store.ingest(canonical, receivedAt, null);
    }
}
