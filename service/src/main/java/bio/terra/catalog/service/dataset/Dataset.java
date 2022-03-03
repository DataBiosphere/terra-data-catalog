package bio.terra.catalog.service.dataset;

import java.time.Instant;
import java.util.UUID;

public record Dataset(
    UUID id, String datasetId, String storageSystem, String metadata, Instant createdDate) {}
