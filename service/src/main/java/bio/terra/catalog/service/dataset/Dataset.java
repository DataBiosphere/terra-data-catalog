package bio.terra.catalog.service.dataset;

import java.time.Instant;
import java.util.UUID;

public class Dataset {
  private UUID id;
  private String datasetId;
  private String storageSystem;
  private String metadata;
  private Instant createdDate;

  public UUID getId() {
    return id;
  }

  public Dataset id(UUID id) {
    this.id = id;
    return this;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public Dataset datasetId(String datasetId) {
    this.datasetId = datasetId;
    return this;
  }

  public String getStorageSystem() {
    return storageSystem;
  }

  public Dataset storageSystem(String storageSystem) {
    this.storageSystem = storageSystem;
    return this;
  }

  public String getMetadata() {
    return metadata;
  }

  public Dataset metadata(String metadata) {
    this.metadata = metadata;
    return this;
  }

  public Instant getCreatedDate() {
    return createdDate;
  }

  public Dataset createdDate(Instant createdDate) {
    this.createdDate = createdDate;
    return this;
  }
}
