package com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface WorkItemJpaRepository extends JpaRepository<WorkItemEntity, UUID>, JpaSpecificationExecutor<WorkItemEntity> {
    Optional<WorkItemEntity> findByItemNo(String itemNo);
}
