package bio.terra.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.rawls")
public record RawlsConfiguration(String basePath, String resourceId) {}
