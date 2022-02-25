package bio.terra.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.datarepo")
public record DatarepoConfiguration(String basePath) {}
