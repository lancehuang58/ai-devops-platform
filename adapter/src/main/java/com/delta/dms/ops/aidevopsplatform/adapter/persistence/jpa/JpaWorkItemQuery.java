package com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa;

import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemQuery;
import com.delta.dms.ops.aidevopsplatform.service.workflow.TimelineEvent;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemDetail;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemListQuery;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemPage;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemSummary;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

public final class JpaWorkItemQuery implements WorkItemQuery {
    private final WorkItemJpaRepository items;
    private final CaseEventJpaRepository events;

    public JpaWorkItemQuery(WorkItemJpaRepository items, CaseEventJpaRepository events) {
        this.items = items;
        this.events = events;
    }

    @Override
    public WorkItemPage search(WorkItemListQuery query) {
        var page = items.findAll(filter(query), PageRequest.of(Math.max(query.page(), 1) - 1, query.size(),
                Sort.by(Sort.Direction.DESC, "updatedAt")));
        var rows = page.getContent().stream().map(JpaWorkItemQuery::summary).toList();
        return new WorkItemPage(rows, query.page(), query.size(), page.getTotalPages(), page.getTotalElements());
    }

    @Override
    public Optional<WorkItemDetail> findByItemNo(String itemNo) {
        return items.findByItemNo(itemNo).map(item -> {
            var timeline = events.findByItemIdOrderBySeqAsc(item.getItemId()).stream()
                    .map(event -> new TimelineEvent(event.getSeq(), event.getEventType(), event.getDetail(), event.getOccurredAt()))
                    .toList();
            return new WorkItemDetail(item.getItemNo(), item.getItemType(), item.getStatus(), item.getTitle(),
                    item.getEnvironment(), item.getPrimaryResourceId(), item.getClosureReason(), item.getSuggestedClosureReason(),
                    item.getBlockedReason(), item.getRequestorPrincipalId(), item.getActiveTakeoverId() != null,
                    item.getUpdatedAt(), timeline);
        });
    }

    private static Specification<WorkItemEntity> filter(WorkItemListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (query.status() != null && !query.status().isBlank()) predicates.add(cb.equal(root.get("status"), query.status()));
            else if (query.openOnly()) predicates.add(cb.notEqual(root.get("status"), "CLOSED"));
            if (query.updatedFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), query.updatedFrom()));
            if (query.updatedTo() != null) predicates.add(cb.lessThan(root.get("updatedAt"), query.updatedTo()));
            if (query.system() != null && !query.system().isBlank()) {
                Subquery<UUID> labels = criteria.subquery(UUID.class);
                Root<WorkItemLabelEntity> label = labels.from(WorkItemLabelEntity.class);
                labels.select(label.get("itemId")).where(cb.equal(label.get("itemId"), root.get("itemId")),
                        cb.or(cb.equal(label.get("labelName"), query.system()), cb.equal(label.get("labelId"), query.system())));
                predicates.add(cb.or(cb.equal(root.get("primaryResourceId"), query.system()), cb.exists(labels)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static WorkItemSummary summary(WorkItemEntity item) {
        return new WorkItemSummary(item.getItemNo(), item.getItemType(), item.getStatus(), item.getTitle(),
                item.getEnvironment(), item.getPrimaryResourceId(), item.getUpdatedAt());
    }
}
