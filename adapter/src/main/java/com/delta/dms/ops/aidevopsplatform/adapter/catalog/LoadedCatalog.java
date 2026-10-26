package com.delta.dms.ops.aidevopsplatform.adapter.catalog;

import com.delta.dms.ops.aidevopsplatform.model.catalog.ServiceCatalog;

public record LoadedCatalog(PlatformServicesProperties properties, SecretBundle secrets, ServiceCatalog catalog) {
    @Override public String toString() {
        return "LoadedCatalog{" + properties + ", " + secrets + "}";
    }
}
