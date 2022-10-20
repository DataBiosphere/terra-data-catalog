package bio.terra.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.schema")
public record SchemaConfiguration(String basePath) {}
