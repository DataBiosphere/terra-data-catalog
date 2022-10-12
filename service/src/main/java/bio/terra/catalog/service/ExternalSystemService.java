package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.common.StorageSystemService;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.iam.AuthenticatedUserRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExternalSystemService implements StorageSystemService {
  @Override
  public Map<String, StorageSystemInformation> getDatasets(AuthenticatedUserRequest user) {
    // This can be implemented by returning the storage IDs for all EXT datasets in the database.
    // Role is always DISCOVERER.
    return Map.of();
  }

  @Override
  public DatasetAccessLevel getRole(AuthenticatedUserRequest user, String storageSourceId) {
    return DatasetAccessLevel.DISCOVERER;
  }

  @Override
  public List<TableMetadata> getPreviewTables(
      AuthenticatedUserRequest user, String storageSourceId) {
    throw new UnsupportedOperationException("preview not supported for external datasets");
  }

  @Override
  public SystemStatusSystems status() {
    return new SystemStatusSystems().ok(true);
  }

  @Override
  public void exportToWorkspace(
      AuthenticatedUserRequest user, String storageSourceId, String workspaceIdDest) {
    throw new UnsupportedOperationException(
        "export to workspace not supported for external datasets");
  }

  @Override
  public DatasetPreviewTable previewTable(
      AuthenticatedUserRequest user, String storageSourceId, String tableName, int maxRows) {
    throw new UnsupportedOperationException("preview not supported for external datasets");
  }
}
