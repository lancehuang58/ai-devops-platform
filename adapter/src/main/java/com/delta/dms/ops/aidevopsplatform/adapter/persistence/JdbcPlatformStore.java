package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import com.delta.dms.ops.aidevopsplatform.model.alert.AlertGroup;
import com.delta.dms.ops.aidevopsplatform.model.alert.AlertGroupState;
import com.delta.dms.ops.aidevopsplatform.model.alert.AlertTiming;
import com.delta.dms.ops.aidevopsplatform.model.alert.CanonicalAlert;
import com.delta.dms.ops.aidevopsplatform.model.alert.CorrelationBinding;
import com.delta.dms.ops.aidevopsplatform.model.alert.Fingerprint;
import com.delta.dms.ops.aidevopsplatform.model.alert.GroupStatus;
import com.delta.dms.ops.aidevopsplatform.model.alert.IngestResult;
import com.delta.dms.ops.aidevopsplatform.model.alert.PendingNotice;
import com.delta.dms.ops.aidevopsplatform.model.alert.Severity;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.sync.DueSync;
import com.delta.dms.ops.aidevopsplatform.model.sync.SyncRetryPolicy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL unit of work for ingestion, the work queue, resource locks and Jira sync. */
public final class JdbcPlatformStore {
    private static final AlertTiming TIMING = AlertTiming.defaults();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SyncRetryPolicy retries = new SyncRetryPolicy();

    public JdbcPlatformStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public static JdbcPlatformStore migrateAndOpen(String jdbcUrl, String username, String password) {
        var config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(16);
        var dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcPlatformStore(dataSource);
    }

    public IngestResult ingest(CanonicalAlert alert, Instant now, CorrelationBinding binding) {
        return tx.execute(status -> ingestInTransaction(alert, now, binding));
    }

    private IngestResult ingestInTransaction(CanonicalAlert alert, Instant now, CorrelationBinding binding) {
        ensureSource(alert);
        if (!insertReceipt(alert, now)) {
            jdbc.update("UPDATE alert_receipt SET duplicate_count = duplicate_count + 1, last_received_at = ? WHERE source_id = ? AND source_event_id = ?",
                    ts(now), alert.sourceId(), alert.sourceEventId());
            return IngestResult.duplicate(findGroupId(alert.fingerprint()));
        }
        var locked = lockGroup(alert.fingerprint());
        if (locked == null) {
            var created = insertGroup(alert, now);
            if (created == null) {
                locked = lockGroup(alert.fingerprint());
            } else if (binding == null) {
                var itemId = openCase(created, alert, now, "monitor signal");
                linkReceipt(alert, created);
                return IngestResult.opened(created, itemId);
            } else {
                var candidate = joinCandidate(created, binding, alert, now);
                linkReceipt(alert, created);
                return IngestResult.candidate(created, candidate);
            }
        }
        var itemId = accumulate(locked, alert, now);
        linkReceipt(alert, locked);
        return IngestResult.counted(locked, itemId);
    }

    public void releaseDueHolds(Instant now) {
        tx.executeWithoutResult(status -> {
            var due = jdbc.query("SELECT candidate_id FROM incident_candidate WHERE status = 'HOLDING' AND hold_until <= ? FOR UPDATE",
                    (rs, n) -> rs.getObject(1, UUID.class), ts(now));
            for (var candidateId : due) {
                var members = memberCount(candidateId);
                if (members != 1) {
                    jdbc.update("UPDATE incident_candidate SET status = 'AWAITING_CONFIRMATION' WHERE candidate_id = ?", candidateId);
                    continue;
                }
                var groupId = jdbc.queryForObject("SELECT group_id FROM incident_candidate_member WHERE candidate_id = ?",
                        UUID.class, candidateId);
                openCaseForGroup(groupId, candidateId, now, "RELEASED_SINGLE");
            }
        });
    }

    public UUID confirmCandidate(UUID candidateId, Instant now) {
        return tx.execute(status -> {
            var existing = jdbc.query("SELECT item_id FROM incident_candidate WHERE candidate_id = ? FOR UPDATE",
                    (rs, n) -> rs.getObject(1, UUID.class), candidateId);
            if (!existing.isEmpty() && existing.getFirst() != null) return existing.getFirst();
            var groupId = jdbc.queryForObject(
                    "SELECT group_id FROM incident_candidate_member WHERE candidate_id = ? ORDER BY group_id LIMIT 1",
                    UUID.class, candidateId);
            return openCaseForGroup(groupId, candidateId, now, "CONFIRMED");
        });
    }

    public int countOpenGroups(String fingerprint) {
        return jdbc.queryForObject("SELECT count(*) FROM alert_group WHERE fingerprint = ? AND status <> 'CLOSED'", Integer.class, fingerprint);
    }

    public int countOpenWorkItemsForResource(String resourceId) {
        return jdbc.queryForObject("SELECT count(*) FROM work_item WHERE primary_resource_id = ? AND status <> 'CLOSED'", Integer.class, resourceId);
    }

    public long occurrenceCount(String fingerprint) {
        var value = jdbc.queryForObject("SELECT occurrence_count FROM alert_group WHERE fingerprint = ? AND status <> 'CLOSED'", Long.class, fingerprint);
        return value == null ? 0 : value;
    }

    public GroupStatus groupStatus(String fingerprint) {
        var status = jdbc.queryForObject("SELECT status FROM alert_group WHERE fingerprint = ? AND status <> 'CLOSED'", String.class, fingerprint);
        return GroupStatus.valueOf(status);
    }

    public boolean isFlapping(String fingerprint) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_flapping FROM alert_group WHERE fingerprint = ? AND status <> 'CLOSED'", Boolean.class, fingerprint));
    }

    public int notificationCount(String dedupKey) {
        return jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE dedup_key = ?", Integer.class, dedupKey);
    }

    public int workItemsForCandidate(UUID candidateId) {
        return jdbc.queryForObject("SELECT count(*) FROM incident_candidate WHERE candidate_id = ? AND item_id IS NOT NULL", Integer.class, candidateId);
    }

    public int syncRequestsForResource(String resourceId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM external_sync_request r
                JOIN work_item w ON w.item_id = r.item_id
                WHERE w.primary_resource_id = ?
                """, Integer.class, resourceId);
    }

    public void sweepRecovering(Instant now) {
        tx.executeWithoutResult(status -> {
            var ids = jdbc.query("SELECT group_id FROM alert_group WHERE status = 'RECOVERING' FOR UPDATE",
                    (rs, n) -> rs.getObject(1, UUID.class));
            for (var id : ids) {
                var group = AlertGroup.rehydrate(loadState(id));
                group.confirmStable(now, TIMING);
                saveState(id, group.state(), now);
            }
        });
    }

    public UUID insertWaitingItem(String itemNo, String resourceId) {
        var itemId = UUID.randomUUID();
        var now = Instant.parse("2026-10-26T08:00:00Z");
        jdbc.update("""
                INSERT INTO work_item (item_id, item_no, item_type, status, title, requestor_principal_id, source_kind,
                    environment, primary_resource_id, record_completeness, row_version, created_at, updated_at, status_changed_at)
                VALUES (?, ?, 'INCIDENT', 'PENDING_APPROVAL', ?, 'ops', 'USER', 'PROD', ?, 'PENDING', 0, ?, ?, ?)
                """, itemId, itemNo, itemNo, resourceId, ts(now), ts(now), ts(now));
        return itemId;
    }

    public void finishReadyTasks() {
        jdbc.update("UPDATE work_task SET status = 'DONE' WHERE status = 'READY'");
    }

    public void enqueueTask(UUID itemId, String dedupKey, Instant runAfter) {
        jdbc.update("""
                INSERT INTO work_task (task_id, item_id, task_kind, dispatching, run_after, status, dedup_key, created_at)
                VALUES (?, ?, 'ADVANCE', false, ?, 'READY', ?, ?)
                """, UUID.randomUUID(), itemId, ts(runAfter), dedupKey, ts(runAfter));
    }

    public Optional<TaskClaim> claimNext(String worker, Instant now, Duration lease) {
        try {
            return tx.execute(status -> {
                var rows = jdbc.query("""
                        UPDATE work_task t
                           SET status = 'CLAIMED', claimed_by = ?, lease_until = ?, attempts = attempts + 1
                         WHERE t.task_id = (
                            SELECT w.task_id FROM work_task w
                             WHERE w.status = 'READY' AND w.run_after <= ?
                               AND NOT EXISTS (
                                   SELECT 1 FROM work_task c WHERE c.item_id = w.item_id AND c.status = 'CLAIMED')
                             ORDER BY w.run_after, w.task_id
                             FOR UPDATE SKIP LOCKED
                             LIMIT 1)
                        RETURNING t.task_id, t.item_id
                        """, (rs, n) -> new TaskClaim(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
                        worker, ts(now.plus(lease)), ts(now));
                return rows.stream().findFirst();
            });
        } catch (DuplicateKeyException conflict) {
            return Optional.empty();
        }
    }

    public int claimedCount(UUID itemId) {
        return jdbc.queryForObject("SELECT count(*) FROM work_task WHERE item_id = ? AND status = 'CLAIMED'", Integer.class, itemId);
    }

    public int readyCount(UUID itemId) {
        return jdbc.queryForObject("SELECT count(*) FROM work_task WHERE item_id = ? AND status = 'READY'", Integer.class, itemId);
    }

    public boolean tryAcquire(UUID itemId, String resourceId, String phase, String owner, long epoch, Instant now, Duration lease) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            var inserted = jdbc.update("""
                    INSERT INTO resource_lock (resource_id, holder_item_id, holder_phase, owner_instance, owner_epoch, lease_until, status, acquired_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                    ON CONFLICT (resource_id) DO NOTHING
                    """, resourceId, itemId, phase, owner, epoch, ts(now.plus(lease)), ts(now));
            if (inserted == 1) {
                jdbc.update("UPDATE work_item SET blocked_reason = NULL, updated_at = ? WHERE item_id = ?", ts(now), itemId);
                return true;
            }
            jdbc.update("UPDATE work_item SET blocked_reason = 'RESOURCE_LOCK_WAIT', updated_at = ? WHERE item_id = ?", ts(now), itemId);
            jdbc.update("""
                    INSERT INTO resource_lock_waiter (waiter_id, resource_id, item_id, enqueued_at, status)
                    VALUES (?, ?, ?, ?, 'WAITING')
                    """, UUID.randomUUID(), resourceId, itemId, ts(now));
            return false;
        }));
    }

    public void expireLocks(Instant now) {
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM resource_lock WHERE lease_until < ? AND status = 'ACTIVE' AND holder_phase IN ('QUEUED', 'PRECHECKING')", ts(now));
            jdbc.update("UPDATE resource_lock SET status = 'STALE' WHERE lease_until < ? AND holder_phase NOT IN ('QUEUED', 'PRECHECKING')", ts(now));
        });
    }

    public boolean lockHeld(String resourceId) {
        return jdbc.queryForObject("SELECT count(*) FROM resource_lock WHERE resource_id = ?", Integer.class, resourceId) > 0;
    }

    public String blockedReason(UUID itemId) {
        return jdbc.queryForObject("SELECT blocked_reason FROM work_item WHERE item_id = ?", String.class, itemId);
    }

    public UUID enqueueSync(UUID itemId, String dedupKey, Instant now) {
        var requestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO external_sync_request (request_id, item_id, destination, operation, payload_hash, status, attempt_count, next_attempt_at, dedup_key, created_at)
                VALUES (?, ?, 'JIRA', 'CREATE', ?, 'PENDING', 0, ?, ?, ?)
                """, requestId, itemId, Fingerprint.sha256(dedupKey), ts(now), dedupKey, ts(now));
        return requestId;
    }

    public Optional<UUID> claimSync(UUID requestId, Instant now, String worker) {
        return tx.execute(status -> {
            var rows = jdbc.query("""
                    UPDATE external_sync_request
                       SET status = 'SENDING', row_version = row_version + 1
                     WHERE request_id = ?
                       AND status IN ('PENDING', 'RETRY_WAIT')
                       AND next_attempt_at <= ?
                    RETURNING request_id
                    """, (rs, n) -> rs.getObject(1, UUID.class), requestId, ts(now));
            return rows.stream().findFirst();
        });
    }

    public SyncProgress failSync(UUID requestId, Instant now, double jitterUnit) {
        return tx.execute(status -> {
            int current = jdbc.queryForObject("SELECT attempt_count FROM external_sync_request WHERE request_id = ? FOR UPDATE", Integer.class, requestId);
            int attempts = current + 1;
            var decision = retries.afterTransientFailure(attempts, now, jitterUnit);
            jdbc.update("INSERT INTO external_sync_attempt (attempt_id, request_id, attempted_at, outcome, error_class) VALUES (?, ?, ?, 'FAILED', 'TRANSIENT')",
                    UUID.randomUUID(), requestId, ts(now));
            if (decision.stop()) {
                jdbc.update("UPDATE external_sync_request SET attempt_count = ?, status = 'PENDING_HUMAN', row_version = row_version + 1 WHERE request_id = ?",
                        attempts, requestId);
                return new SyncProgress(true, attempts, null);
            }
            jdbc.update("UPDATE external_sync_request SET attempt_count = ?, status = 'RETRY_WAIT', next_attempt_at = ?, row_version = row_version + 1 WHERE request_id = ?",
                    attempts, ts(decision.nextAttemptAt()), requestId);
            return new SyncProgress(false, attempts, decision.nextAttemptAt());
        });
    }

    public String syncStatus(UUID requestId) {
        return jdbc.queryForObject("SELECT status FROM external_sync_request WHERE request_id = ?", String.class, requestId);
    }

    public int syncAttempts(UUID requestId) {
        return jdbc.queryForObject("SELECT attempt_count FROM external_sync_request WHERE request_id = ?", Integer.class, requestId);
    }

    public List<DueSync> claimDueSyncs(Instant now, int limit) {
        return tx.execute(status -> {
            var claimed = jdbc.query("""
                    WITH due AS (
                        SELECT request_id FROM external_sync_request
                         WHERE status IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at <= ?
                         ORDER BY next_attempt_at, request_id
                         FOR UPDATE SKIP LOCKED
                         LIMIT ?)
                    UPDATE external_sync_request r
                       SET status = 'SENDING', row_version = row_version + 1
                      FROM due
                     WHERE r.request_id = due.request_id
                    RETURNING r.request_id, r.item_id
                    """, (rs, n) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, ts(now), limit);
            var due = new java.util.ArrayList<DueSync>();
            for (var row : claimed) {
                var item = jdbc.queryForObject("SELECT item_no, title FROM work_item WHERE item_id = ?",
                        (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, row[1]);
                due.add(new DueSync(row[0], item[0], item[1]));
            }
            return due;
        });
    }

    public void succeedSync(UUID requestId, Instant now) {
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE external_sync_request SET status = 'SUCCEEDED', row_version = row_version + 1 WHERE request_id = ?", requestId);
            jdbc.update("INSERT INTO external_sync_attempt (attempt_id, request_id, attempted_at, outcome, error_class) VALUES (?, ?, ?, 'SUCCEEDED', NULL)",
                    UUID.randomUUID(), requestId, ts(now));
        });
    }

    public void completeTask(UUID taskId) {
        jdbc.update("UPDATE work_task SET status = 'DONE', lease_until = NULL WHERE task_id = ?", taskId);
    }

    public String taskStatus(UUID taskId) {
        return jdbc.queryForObject("SELECT status FROM work_task WHERE task_id = ?", String.class, taskId);
    }

    public void postponeSyncs(Instant when) {
        jdbc.update("UPDATE external_sync_request SET next_attempt_at = ? WHERE status IN ('PENDING', 'RETRY_WAIT')", ts(when));
    }

    public int taskCount(UUID itemId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM work_task WHERE item_id = ? AND status = ?", Integer.class, itemId, status);
    }

    public String primaryResource(UUID itemId) {
        return jdbc.queryForObject("SELECT primary_resource_id FROM work_item WHERE item_id = ?", String.class, itemId);
    }

    public List<PendingNotice> pendingNotifications() {
        return jdbc.query("SELECT dedup_key, body FROM notification_outbox WHERE status = 'PENDING' ORDER BY updated_at",
                (rs, n) -> new PendingNotice(rs.getString(1), rs.getString(2)));
    }

    public void markNotificationSent(String dedupKey, Instant now) {
        jdbc.update("UPDATE notification_outbox SET status = 'SENT', updated_at = ? WHERE dedup_key = ?", ts(now), dedupKey);
    }

    public void saveProbeResults(String instanceId, List<ChannelAttempt> attempts, String disposition, Instant observedAt) {
        for (var attempt : attempts) {
            jdbc.update("""
                    INSERT INTO probe_result (probe_result_id, instance_id, channel, verdict, reason, disposition, observed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), instanceId, attempt.channel().name(), attempt.verdict().name(),
                    attempt.detail() == null ? "" : attempt.detail(), disposition, ts(observedAt));
        }
    }

    public void enqueueNoticeOnce(String dedupKey, String kind, String body, Instant now) {
        jdbc.update("""
                INSERT INTO notification_outbox (notification_id, dedup_key, kind, body, status, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?)
                ON CONFLICT (dedup_key) DO NOTHING
                """, UUID.randomUUID(), dedupKey, kind, body, ts(now));
    }

    public void recordProbeTrail(UUID itemId, String detail, Instant now) {
        jdbc.update("""
                INSERT INTO work_activity (activity_id, item_id, activity_kind, started_at, finished_at, detail)
                VALUES (?, ?, 'INVESTIGATION', ?, ?, ?)
                """, UUID.randomUUID(), itemId, ts(now), ts(now), detail);
        var seq = jdbc.queryForObject("SELECT COALESCE(MAX(seq), 0) + 1 FROM case_event WHERE item_id = ?", Integer.class, itemId);
        jdbc.update("""
                INSERT INTO case_event (event_id, item_id, seq, event_type, detail, occurred_at, aggregate_version)
                VALUES (?, ?, ?, 'PROBE_OBSERVED', ?, ?, 0)
                """, UUID.randomUUID(), itemId, seq, detail, ts(now));
    }

    public int probeResultCount(String instanceId) {
        return jdbc.queryForObject("SELECT count(*) FROM probe_result WHERE instance_id = ?", Integer.class, instanceId);
    }

    public String openWorkItemTitle(String resourceId) {
        return jdbc.queryForObject("""
                SELECT title FROM work_item WHERE primary_resource_id = ? AND status <> 'CLOSED' ORDER BY created_at LIMIT 1
                """, String.class, resourceId);
    }

    public String activityDetail(String resourceId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(string_agg(a.detail, E'\\n'), '') FROM work_activity a
                JOIN work_item w ON w.item_id = a.item_id
                WHERE w.primary_resource_id = ?
                """, String.class, resourceId);
    }

    public String sourceType(String sourceId) {
        return jdbc.queryForObject("SELECT source_type FROM monitor_source WHERE source_id = ?", String.class, sourceId);
    }

    private void ensureSource(CanonicalAlert alert) {
        jdbc.update("""
                INSERT INTO monitor_source (source_id, source_type, display_name, normalizer_ref, enabled)
                VALUES (?, ?, ?, ?, true) ON CONFLICT (source_id) DO NOTHING
                """, alert.sourceId(), alert.sourceType().name(), alert.sourceId(), alert.normalizer());
    }

    private boolean insertReceipt(CanonicalAlert alert, Instant now) {
        var rows = jdbc.query("""
                INSERT INTO alert_receipt (receipt_id, source_id, source_event_id, payload_sha256, raw_alert, first_received_at,
                    last_received_at, duplicate_count, normalize_status, event_id, fingerprint, event_status, observed_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 0, 'NORMALIZED', ?, ?, ?, ?)
                ON CONFLICT (source_id, source_event_id) DO NOTHING
                RETURNING receipt_id
                """, (rs, n) -> rs.getObject(1, UUID.class),
                alert.receiptId(), alert.sourceId(), alert.sourceEventId(), Fingerprint.sha256(alert.summary()),
                "{\"summary\":\"" + escape(alert.summary()) + "\"}", ts(now), ts(now), alert.eventId(), alert.fingerprint(),
                alert.status().name(), ts(alert.observedAt()));
        return !rows.isEmpty();
    }

    private UUID insertGroup(CanonicalAlert alert, Instant now) {
        var groupId = UUID.randomUUID();
        var rows = jdbc.query("""
                INSERT INTO alert_group (group_id, fingerprint, fingerprint_version, active_fingerprint, environment, resource_id,
                    resource_resolution, symptom_type, discriminator, status, severity_current, severity_max, first_seen_at,
                    last_seen_at, last_firing_at, last_state_change_at, occurrence_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, 'FIRING', ?, ?, ?, ?, ?, ?, 1, ?)
                ON CONFLICT (active_fingerprint) DO NOTHING
                RETURNING group_id
                """, (rs, n) -> rs.getObject(1, UUID.class),
                groupId, alert.fingerprint(), alert.fingerprintVersion(), alert.fingerprint(), alert.environment(),
                alert.resourceId(), alert.resourceResolved() ? "RESOLVED" : "UNRESOLVED", alert.symptomType(),
                alert.severity().name(), alert.severity().name(), ts(alert.observedAt()), ts(alert.observedAt()),
                ts(alert.observedAt()), ts(alert.observedAt()), ts(now));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private UUID lockGroup(String fingerprint) {
        var rows = jdbc.query("SELECT group_id FROM alert_group WHERE active_fingerprint = ? FOR UPDATE",
                (rs, n) -> rs.getObject(1, UUID.class), fingerprint);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private UUID findGroupId(String fingerprint) {
        var rows = jdbc.query("SELECT group_id FROM alert_group WHERE fingerprint = ? AND status <> 'CLOSED'",
                (rs, n) -> rs.getObject(1, UUID.class), fingerprint);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private UUID accumulate(UUID groupId, CanonicalAlert alert, Instant now) {
        var group = AlertGroup.rehydrate(loadState(groupId));
        group.apply(alert.status(), alert.severity(), alert.observedAt(), TIMING, now);
        saveState(groupId, group.state(), now);
        var items = jdbc.query("SELECT item_id FROM alert_group WHERE group_id = ?", (rs, n) -> rs.getObject(1, UUID.class), groupId);
        return items.isEmpty() ? null : items.getFirst();
    }

    private UUID joinCandidate(UUID groupId, CorrelationBinding binding, CanonicalAlert alert, Instant now) {
        var rows = jdbc.query("""
                INSERT INTO incident_candidate (candidate_id, active_key, rule_id, environment, upstream_resource_id, status, hold_until, created_at)
                VALUES (?, ?, ?, ?, ?, 'HOLDING', ?, ?)
                ON CONFLICT (active_key) DO NOTHING
                RETURNING candidate_id
                """, (rs, n) -> rs.getObject(1, UUID.class),
                UUID.randomUUID(), binding.activeKey(), binding.ruleId(), binding.environment(), binding.upstreamResourceId(),
                ts(now.plusSeconds(60)), ts(now));
        UUID candidateId = rows.isEmpty()
                ? jdbc.queryForObject("SELECT candidate_id FROM incident_candidate WHERE active_key = ?", UUID.class, binding.activeKey())
                : rows.getFirst();
        jdbc.update("INSERT INTO incident_candidate_member (candidate_id, group_id) VALUES (?, ?) ON CONFLICT DO NOTHING", candidateId, groupId);
        if (memberCount(candidateId) > 1) {
            jdbc.update("UPDATE incident_candidate SET status = 'AWAITING_CONFIRMATION' WHERE candidate_id = ?", candidateId);
        }
        var body = "candidate " + binding.activeKey() + " members=" + memberCount(candidateId) + " " + alert.summary();
        jdbc.update("""
                INSERT INTO notification_outbox (notification_id, dedup_key, kind, body, status, updated_at)
                VALUES (?, ?, 'CANDIDATE', ?, 'PENDING', ?)
                ON CONFLICT (dedup_key) DO UPDATE SET body = EXCLUDED.body, updated_at = EXCLUDED.updated_at
                """, UUID.randomUUID(), binding.activeKey(), body, ts(now));
        return candidateId;
    }

    private int memberCount(UUID candidateId) {
        return jdbc.queryForObject("SELECT count(*) FROM incident_candidate_member WHERE candidate_id = ?", Integer.class, candidateId);
    }

    private UUID openCaseForGroup(UUID groupId, UUID candidateId, Instant now, String reason) {
        var resource = jdbc.queryForObject("SELECT resource_id FROM alert_group WHERE group_id = ?", String.class, groupId);
        var environment = jdbc.queryForObject("SELECT environment FROM alert_group WHERE group_id = ?", String.class, groupId);
        var summary = jdbc.queryForObject("SELECT symptom_type FROM alert_group WHERE group_id = ?", String.class, groupId);
        var alert = new CanonicalAlert("1.0", UUID.randomUUID(), com.delta.dms.ops.aidevopsplatform.model.alert.SourceType.PROMETHEUS_ALERTMANAGER,
                "platform", "alertmanager-v4@1", "case", null, "fp", Fingerprint.VERSION, resource, "VM", true, environment,
                List.of(), summary, summary, java.util.Map.of(), Severity.MAJOR,
                com.delta.dms.ops.aidevopsplatform.model.alert.SignalStatus.FIRING, now, now, null, now, summary, UUID.randomUUID());
        var itemId = openCase(groupId, alert, now, reason);
        var members = jdbc.query("SELECT group_id FROM incident_candidate_member WHERE candidate_id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), candidateId);
        for (var member : members) {
            jdbc.update("UPDATE alert_group SET item_id = ? WHERE group_id = ? AND item_id IS NULL", itemId, member);
        }
        jdbc.update("UPDATE incident_candidate SET status = ?, item_id = ?, active_key = NULL WHERE candidate_id = ?", reason, itemId, candidateId);
        return itemId;
    }

    private UUID openCase(UUID groupId, CanonicalAlert alert, Instant now, String reason) {
        var itemId = UUID.randomUUID();
        var number = jdbc.queryForObject("SELECT nextval('work_item_no_seq')", Long.class);
        var itemNo = "OPS-" + String.format("%010d", number);
        jdbc.update("""
                INSERT INTO work_item (item_id, item_no, item_type, status, title, requestor_principal_id, source_kind,
                    environment, primary_resource_id, signal_state, record_completeness, row_version, created_at, updated_at, status_changed_at)
                VALUES (?, ?, 'INCIDENT', 'NEW', ?, 'monitor', 'MONITOR', ?, ?, 'FIRING', 'PENDING', 0, ?, ?, ?)
                """, itemId, itemNo, alert.summary(), alert.environment(), alert.resourceId(), ts(now), ts(now), ts(now));
        for (var service : alert.serviceRefs()) {
            jdbc.update("INSERT INTO work_item_label (item_id, label_id, label_name, label_kind) VALUES (?, ?, ?, 'SERVICE')",
                    itemId, service, service);
        }
        jdbc.update("""
                INSERT INTO case_event (event_id, item_id, seq, event_type, detail, occurred_at, aggregate_version)
                VALUES (?, ?, 1, 'WORK_ITEM_OPENED', ?, ?, 0)
                """, UUID.randomUUID(), itemId, reason, ts(now));
        jdbc.update("UPDATE alert_group SET item_id = ? WHERE group_id = ?", itemId, groupId);
        jdbc.update("""
                INSERT INTO work_task (task_id, item_id, task_kind, dispatching, run_after, status, dedup_key, created_at)
                VALUES (?, ?, 'ADVANCE', false, ?, 'READY', ?, ?)
                """, UUID.randomUUID(), itemId, ts(now), "advance:" + itemId, ts(now));
        jdbc.update("""
                INSERT INTO external_sync_request (request_id, item_id, destination, operation, payload_hash, status, attempt_count, next_attempt_at, dedup_key, created_at)
                VALUES (?, ?, 'JIRA', 'CREATE', ?, 'PENDING', 0, ?, ?, ?)
                """, UUID.randomUUID(), itemId, Fingerprint.sha256(itemId.toString()), ts(now), "jira:create:" + itemId, ts(now));
        return itemId;
    }

    private void linkReceipt(CanonicalAlert alert, UUID groupId) {
        jdbc.update("UPDATE alert_receipt SET group_id = ? WHERE source_id = ? AND source_event_id = ?",
                groupId, alert.sourceId(), alert.sourceEventId());
    }

    private AlertGroupState loadState(UUID groupId) {
        return jdbc.queryForObject("SELECT * FROM alert_group WHERE group_id = ?", (rs, n) -> new AlertGroupState(
                GroupStatus.valueOf(rs.getString("status")),
                Severity.valueOf(rs.getString("severity_current")),
                Severity.valueOf(rs.getString("severity_max")),
                rs.getBoolean("is_flapping"),
                instant(rs, "flap_window_start"),
                rs.getInt("flap_transitions"),
                rs.getLong("occurrence_count"),
                rs.getLong("suppressed_count"),
                instant(rs, "last_state_change_at"),
                instant(rs, "recovering_since"),
                instant(rs, "reopen_until"),
                rs.getInt("group_epoch")), groupId);
    }

    private void saveState(UUID groupId, AlertGroupState state, Instant now) {
        jdbc.update("""
                UPDATE alert_group
                   SET status = ?, is_flapping = ?, group_epoch = ?, severity_current = ?, severity_max = ?,
                       last_seen_at = ?, last_state_change_at = ?, recovering_since = ?, reopen_until = ?,
                       occurrence_count = ?, suppressed_count = ?, flap_window_start = ?, flap_transitions = ?,
                       row_version = row_version + 1
                 WHERE group_id = ?
                """, state.status().name(), state.flapping(), state.epoch(), state.severityCurrent().name(),
                state.severityMax().name(), ts(now), ts(state.lastStateChangeAt()), ts(state.recoveringSince()),
                ts(state.reopenUntil()), state.occurrenceCount(), state.suppressedCount(), ts(state.flapWindowStart()),
                state.flapTransitions(), groupId);
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
