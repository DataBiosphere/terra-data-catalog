package bio.terra.catalog.service.dataset;

import bio.terra.catalog.common.StorageSystem;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;

public record Dataset(
    DatasetId id,
    String storageSourceId,
    StorageSystem storageSystem,
    ObjectNode metadata,
    Instant creationTime) {

  public Dataset(String storageSourceId, StorageSystem storageSystem, ObjectNode metadata) {
    this(null, storageSourceId, storageSystem, metadata, null);
  }

  /**
   * Create a new Dataset with the given metadata
   *
   * @param metadata the metadata to store
   */
  public Dataset withMetadata(ObjectNode metadata) {
    return new Dataset(id, storageSourceId, storageSystem, metadata, creationTime);
  }
}
