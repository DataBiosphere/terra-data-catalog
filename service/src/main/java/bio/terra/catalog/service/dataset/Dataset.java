package bio.terra.catalog.service.dataset;

import bio.terra.catalog.common.StorageSystem;
import java.time.Instant;

public record Dataset(
    DatasetId id,
    String storageSourceId,
    StorageSystem storageSystem,
    String metadata,
    Instant createdDate) {

  /**
   * Create a new Dataset with the given metadata
   *
   * @param metadata the metadata to store
   */
  public Dataset withMetadata(String metadata) {
    return new Dataset(id, storageSourceId, storageSystem, metadata, createdDate);
  }
}
