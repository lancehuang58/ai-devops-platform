package com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseEventJpaRepository extends JpaRepository<CaseEventEntity, CaseEventEntity.Key> {
    List<CaseEventEntity> findByItemIdOrderBySeqAsc(UUID itemId);
}
