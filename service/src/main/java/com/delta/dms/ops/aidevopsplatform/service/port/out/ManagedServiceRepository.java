package com.delta.dms.ops.aidevopsplatform.service.port.out;

import com.delta.dms.ops.aidevopsplatform.model.resource.ManagedService;
import java.util.List;
import java.util.Optional;
public interface ManagedServiceRepository {
    List<ManagedService> findAll();
    Optional<ManagedService> findById(String id);
}
