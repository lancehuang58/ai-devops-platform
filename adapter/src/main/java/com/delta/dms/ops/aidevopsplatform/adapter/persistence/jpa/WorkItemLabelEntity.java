package com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "work_item_label")
@IdClass(WorkItemLabelEntity.Key.class)
public class WorkItemLabelEntity {
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "item_id")
    private UUID itemId;
    @Id
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "label_id")
    private String labelId;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "label_name", nullable = false)
    private String labelName;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "label_kind", nullable = false)
    private String labelKind;

    public UUID getItemId() { return itemId; }
    public String getLabelName() { return labelName; }
    public String getLabelId() { return labelId; }

    public static final class Key implements Serializable {
        public UUID itemId;
        public String labelId;
        public UUID getItemId() { return itemId; }
        public String getLabelId() { return labelId; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && itemId.equals(key.itemId) && labelId.equals(key.labelId);
        }
        @Override public int hashCode() { return itemId.hashCode() * 31 + labelId.hashCode(); }
    }
}
