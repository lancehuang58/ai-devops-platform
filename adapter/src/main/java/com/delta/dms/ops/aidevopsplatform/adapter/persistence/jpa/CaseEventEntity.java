package com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_event")
@IdClass(CaseEventEntity.Key.class)
public class CaseEventEntity {
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "item_id")
    private UUID itemId;
    @Id
    @Column(name = "seq")
    private int seq;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "detail")
    private String detail;
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public UUID getItemId() { return itemId; }
    public int getSeq() { return seq; }
    public String getEventType() { return eventType; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }

    public static final class Key implements Serializable {
        public UUID itemId;
        public int seq;
        @Override public boolean equals(Object other) {
            return other instanceof Key key && itemId.equals(key.itemId) && seq == key.seq;
        }
        @Override public int hashCode() { return itemId.hashCode() * 31 + seq; }
    }
}
