package bio.terra.catalog.service.dataset;

import bio.terra.catalog.common.StorageSystem;
import java.time.Instant;
import java.util.UUID;

public record Dataset(
    UUID id, String datasetId, StorageSystem storageSystem, String metadata, Instant createdDate) {}
