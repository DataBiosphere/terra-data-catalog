package bio.terra.catalog.common;

import bio.terra.catalog.service.dataset.DatasetAccessLevel;

public record StorageSystemInformation(DatasetAccessLevel datasetAccessLevel, String phsId) {

  public StorageSystemInformation() {
    this(null, null);
  }

  public StorageSystemInformation phsId(String phsId) {
    return new StorageSystemInformation(datasetAccessLevel, phsId);
  }

  public StorageSystemInformation datasetAccessLevel(DatasetAccessLevel datasetAccessLevel) {
    return new StorageSystemInformation(datasetAccessLevel, phsId);
  }
}
