package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import com.delta.dms.ops.aidevopsplatform.model.workitem.BlockedReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ItemType;
import com.delta.dms.ops.aidevopsplatform.model.workitem.SourceKind;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItem;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemStatus;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemStore;
import com.delta.dms.ops.aidevopsplatform.service.workflow.TrackedWorkItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Optimistic work-item persistence. The loaded row version is the write fence. */
public final class JdbcWorkItemStore implements WorkItemStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcWorkItemStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public Optional<TrackedWorkItem> findByItemNo(String itemNo) {
        return load("SELECT " + COLUMNS + " FROM work_item WHERE item_no = ?", itemNo);
    }

    @Override
    public Optional<TrackedWorkItem> findByItemId(UUID itemId) {
        return load("SELECT " + COLUMNS + " FROM work_item WHERE item_id = ?", itemId);
    }

    @Override
    public void save(TrackedWorkItem tracked) {
        tx.executeWithoutResult(status -> {
            var snap = tracked.item().snapshot();
            if (tracked.loadedVersion() < 0) insert(snap);
            else update(tracked, snap);
            var seq = jdbc.queryForObject("SELECT coalesce(max(seq), 0) FROM case_event WHERE item_id = ?", Integer.class, snap.itemId());
            for (var event : tracked.item().pendingEvents()) {
                seq++;
                jdbc.update("""
                        INSERT INTO case_event (event_id, item_id, seq, event_type, detail, occurred_at, aggregate_version)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, event.eventId(), snap.itemId(), seq, event.type(), event.detail(), ts(event.occurredAt()), event.aggregateVersion());
            }
            for (var activity : tracked.item().activities()) {
                jdbc.update("""
                        INSERT INTO work_activity (activity_id, item_id, activity_kind, started_at, finished_at)
                        VALUES (?, ?, ?, ?, ?)
                        """, activity.activityId(), snap.itemId(), activity.kind().name(), ts(activity.startedAt()), ts(activity.finishedAt()));
            }
            if (snap.status() == WorkItemStatus.CLOSED && snap.activeTakeoverId() != null) {
                jdbc.update("UPDATE manual_takeover SET status = 'CLOSED', ended_at = ? WHERE takeover_id = ? AND status = 'ACTIVE'",
                        ts(snap.updatedAt()), snap.activeTakeoverId());
            }
        });
    }

    @Override
    public void recordTakeover(UUID takeoverId, UUID itemId, String operator, String tool, String model, String reason, String fromStatus, Instant now) {
        jdbc.update("""
                INSERT INTO manual_takeover (takeover_id, item_id, status, operator_id, tool_name, model_name, reason, from_status, started_at)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?)
                """, takeoverId, itemId, operator, tool, model, reason, fromStatus, ts(now));
    }

    @Override
    public void recordWriteback(UUID takeoverId, String summary, String rootCause, String actions, String evidence, String operator, Instant now) {
        var version = jdbc.queryForObject("SELECT coalesce(max(version), 0) + 1 FROM takeover_writeback WHERE takeover_id = ?", Integer.class, takeoverId);
        jdbc.update("""
                INSERT INTO takeover_writeback (writeback_id, takeover_id, version, summary, root_cause, actions, evidence, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), takeoverId, version, summary, rootCause, actions, evidence, operator, ts(now));
    }

    @Override
    public void recordPackage(UUID takeoverId, byte[] body, String sha256, String operator, Instant now) {
        var version = jdbc.queryForObject("SELECT coalesce(max(version), 0) + 1 FROM takeover_package WHERE takeover_id = ?", Integer.class, takeoverId);
        jdbc.update("""
                INSERT INTO takeover_package (package_id, takeover_id, version, body, sha256, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), takeoverId, version, body, sha256, operator, ts(now));
    }

    @Override
    public boolean hasWriteback(UUID itemId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM takeover_writeback w
                JOIN manual_takeover t ON t.takeover_id = w.takeover_id
                WHERE t.item_id = ?
                """, Integer.class, itemId);
        return count != null && count > 0;
    }

    @Override
    public boolean firing(UUID itemId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM alert_group WHERE item_id = ? AND status = 'FIRING'", Integer.class, itemId);
        return count != null && count > 0;
    }

    private void insert(WorkItemSnapshot snap) {
        jdbc.update("""
                INSERT INTO work_item (item_id, item_no, item_type, status, closure_reason, suggested_closure_reason,
                    title, requestor_principal_id, source_kind, environment, record_completeness, blocked_reason,
                    active_takeover_id, duplicate_of, row_version, created_at, updated_at, status_changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?)
                """, snap.itemId(), snap.itemNo(), snap.itemType().name(), snap.status().name(), name(snap.closureReason()),
                name(snap.suggestedClosureReason()), snap.title(), snap.requestorPrincipalId(), snap.sourceKind().name(),
                snap.environment(), name(snap.blockedReason()), snap.activeTakeoverId(), snap.duplicateOf(),
                snap.rowVersion(), ts(snap.createdAt()), ts(snap.updatedAt()), ts(snap.statusChangedAt()));
    }

    private void update(TrackedWorkItem tracked, WorkItemSnapshot snap) {
        int updated = jdbc.update("""
                UPDATE work_item
                   SET item_type = ?, status = ?, closure_reason = ?, suggested_closure_reason = ?, title = ?,
                       requestor_principal_id = ?, source_kind = ?, environment = ?, blocked_reason = ?,
                       active_takeover_id = ?, duplicate_of = ?, row_version = ?, updated_at = ?, status_changed_at = ?
                 WHERE item_id = ? AND row_version = ?
                """, snap.itemType().name(), snap.status().name(), name(snap.closureReason()), name(snap.suggestedClosureReason()),
                snap.title(), snap.requestorPrincipalId(), snap.sourceKind().name(), snap.environment(), name(snap.blockedReason()),
                snap.activeTakeoverId(), snap.duplicateOf(), snap.rowVersion(), ts(snap.updatedAt()), ts(snap.statusChangedAt()),
                snap.itemId(), tracked.loadedVersion());
        if (updated != 1) throw new IllegalStateException("work item version conflict");
    }

    private Optional<TrackedWorkItem> load(String sql, Object key) {
        var rows = jdbc.query(sql, (rs, n) -> map(rs), key);
        return rows.stream().findFirst();
    }

    private static TrackedWorkItem map(ResultSet rs) throws SQLException {
        var snapshot = new WorkItemSnapshot(
                rs.getObject("item_id", UUID.class),
                rs.getString("item_no"),
                ItemType.valueOf(rs.getString("item_type")),
                WorkItemStatus.valueOf(rs.getString("status")),
                reason(rs.getString("closure_reason")),
                reason(rs.getString("suggested_closure_reason")),
                rs.getString("title"),
                rs.getString("requestor_principal_id"),
                SourceKind.valueOf(rs.getString("source_kind")),
                rs.getString("environment"),
                blocked(rs.getString("blocked_reason")),
                rs.getObject("active_takeover_id", UUID.class),
                rs.getObject("duplicate_of", UUID.class),
                rs.getLong("row_version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "status_changed_at"));
        return new TrackedWorkItem(WorkItem.rehydrate(snapshot), snapshot.rowVersion());
    }

    private static final String COLUMNS = """
            item_id, item_no, item_type, status, closure_reason, suggested_closure_reason, title,
            requestor_principal_id, source_kind, environment, blocked_reason, active_takeover_id, duplicate_of,
            row_version, created_at, updated_at, status_changed_at
            """;

    private static ClosureReason reason(String value) { return value == null ? null : ClosureReason.valueOf(value); }
    private static BlockedReason blocked(String value) { return value == null ? null : BlockedReason.valueOf(value); }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static Timestamp ts(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
