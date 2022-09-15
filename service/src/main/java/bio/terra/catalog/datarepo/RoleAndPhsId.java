package bio.terra.catalog.datarepo;

import bio.terra.catalog.service.dataset.DatasetAccessLevel;

public class RoleAndPhsId {
  private String phsId;
  private DatasetAccessLevel datasetAccessLevel;

  public RoleAndPhsId(DatasetAccessLevel datasetAccessLevel, String phsId) {
    this.datasetAccessLevel = datasetAccessLevel;
    this.phsId = phsId;
  }

  public String getPhsId() {
    return phsId;
  }

  public DatasetAccessLevel getDatasetAccessLevel() {
    return datasetAccessLevel;
  }
}
