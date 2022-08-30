package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystemService;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.iam.AuthenticatedUserRequest;
import java.util.List;
import java.util.Map;

public class ExternalSystemService implements StorageSystemService {
  @Override
  public Map<String, DatasetAccessLevel> getIdsAndRoles(AuthenticatedUserRequest user) {
    return Map.of();
  }

  @Override
  public DatasetAccessLevel getRole(AuthenticatedUserRequest user, String storageSourceId) {
    return DatasetAccessLevel.DISCOVERER;
  }

  @Override
  public List<TableMetadata> getPreviewTables(
      AuthenticatedUserRequest user, String storageSourceId) {
    return List.of();
  }

  @Override
  public SystemStatusSystems status() {
    return new SystemStatusSystems().ok(true);
  }

  @Override
  public void exportToWorkspace(
      AuthenticatedUserRequest user, String storageSourceId, String workspaceIdDest) {}

  @Override
  public DatasetPreviewTable previewTable(
      AuthenticatedUserRequest user, String storageSourceId, String tableName, int maxRows) {
    return new DatasetPreviewTable();
  }
}
