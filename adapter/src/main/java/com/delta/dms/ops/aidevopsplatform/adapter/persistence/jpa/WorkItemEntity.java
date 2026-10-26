package com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_item")
public class WorkItemEntity {
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "item_id")
    private UUID itemId;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "item_no", nullable = false)
    private String itemNo;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "item_type", nullable = false)
    private String itemType;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "status", nullable = false)
    private String status;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "closure_reason")
    private String closureReason;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "suggested_closure_reason")
    private String suggestedClosureReason;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "title", nullable = false)
    private String title;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "requestor_principal_id", nullable = false)
    private String requestorPrincipalId;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "source_kind", nullable = false)
    private String sourceKind;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "environment", nullable = false)
    private String environment;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "primary_resource_id")
    private String primaryResourceId;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "blocked_reason")
    private String blockedReason;
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "active_takeover_id")
    private UUID activeTakeoverId;
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    public UUID getItemId() { return itemId; }
    public String getItemNo() { return itemNo; }
    public String getItemType() { return itemType; }
    public String getStatus() { return status; }
    public String getClosureReason() { return closureReason; }
    public String getSuggestedClosureReason() { return suggestedClosureReason; }
    public String getTitle() { return title; }
    public String getRequestorPrincipalId() { return requestorPrincipalId; }
    public String getEnvironment() { return environment; }
    public String getPrimaryResourceId() { return primaryResourceId; }
    public String getBlockedReason() { return blockedReason; }
    public UUID getActiveTakeoverId() { return activeTakeoverId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
