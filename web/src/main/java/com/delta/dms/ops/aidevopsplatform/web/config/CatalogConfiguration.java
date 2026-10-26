package com.delta.dms.ops.aidevopsplatform.web.config;

import com.delta.dms.ops.aidevopsplatform.adapter.catalog.CatalogYamlLoader;
import com.delta.dms.ops.aidevopsplatform.adapter.catalog.LoadedCatalog;
import com.delta.dms.ops.aidevopsplatform.adapter.catalog.PlatformServicesProperties;
import com.delta.dms.ops.aidevopsplatform.adapter.catalog.SecretBundle;
import com.delta.dms.ops.aidevopsplatform.model.catalog.CatalogException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@Profile("platform")
public class CatalogConfiguration {
    @Bean
    LoadedCatalog loadedCatalog(Environment environment,
                                @Value("${platform.catalog.file:config/platform-services.yml}") String file) throws IOException {
        var path = Path.of(file);
        if (!Files.isRegularFile(path)) throw new CatalogException("catalog file not found: " + file);
        return new CatalogYamlLoader().load(Files.readString(path), environment::getProperty);
    }

    @Bean PlatformServicesProperties platformServicesProperties(LoadedCatalog loaded) { return loaded.properties(); }
    @Bean SecretBundle secretBundle(LoadedCatalog loaded) { return loaded.secrets(); }
}
