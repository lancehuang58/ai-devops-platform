package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemDetail;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemListQuery;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemPage;

import java.util.Optional;

public interface WorkItemQuery {
    WorkItemPage search(WorkItemListQuery query);
    Optional<WorkItemDetail> findByItemNo(String itemNo);
}
