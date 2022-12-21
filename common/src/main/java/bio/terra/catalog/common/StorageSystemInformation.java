package bio.terra.catalog.common;

import bio.terra.catalog.service.dataset.DatasetAccessLevel;

public record StorageSystemInformation(DatasetAccessLevel datasetAccessLevel, String phsId) {
  public StorageSystemInformation(DatasetAccessLevel accessLevel) {
    this(accessLevel, null);
  }
}
