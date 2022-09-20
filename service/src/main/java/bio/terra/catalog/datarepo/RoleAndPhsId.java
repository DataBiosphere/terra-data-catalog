package bio.terra.catalog.datarepo;

import bio.terra.catalog.service.dataset.DatasetAccessLevel;

public class RoleAndPhsId {
  private String phsId;
  private DatasetAccessLevel datasetAccessLevel;

  public RoleAndPhsId() {
    this.datasetAccessLevel = null;
    this.phsId = null;
  }

  public RoleAndPhsId(DatasetAccessLevel datasetAccessLevel, String phsId) {
    this.datasetAccessLevel = datasetAccessLevel;
    this.phsId = phsId;
  }

  public RoleAndPhsId phsId(String phsId) {
    this.phsId = phsId;
    return this;
  }

  public RoleAndPhsId datasetAccessLevel(DatasetAccessLevel datasetAccessLevel) {
    this.datasetAccessLevel = datasetAccessLevel;
    return this;
  }

  public String getPhsId() {
    return phsId;
  }

  public DatasetAccessLevel getDatasetAccessLevel() {
    return datasetAccessLevel;
  }
}
