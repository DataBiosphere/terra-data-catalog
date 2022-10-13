package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.common.StorageSystemService;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExternalSystemService implements StorageSystemService {
  @Override
  public Map<String, StorageSystemInformation> getDatasets() {
    // This can be implemented by returning the storage IDs for all EXT datasets in the database.
    // Role is always DISCOVERER.
    return Map.of();
  }

  @Override
  public DatasetAccessLevel getRole(String storageSourceId) {
    return DatasetAccessLevel.DISCOVERER;
  }

  @Override
  public List<TableMetadata> getPreviewTables(String storageSourceId) {
    throw new UnsupportedOperationException("preview not supported for external datasets");
  }

  @Override
  public SystemStatusSystems status() {
    return new SystemStatusSystems().ok(true);
  }

  @Override
  public void exportToWorkspace(String storageSourceId, String workspaceIdDest) {
    throw new UnsupportedOperationException(
        "export to workspace not supported for external datasets");
  }

  @Override
  public DatasetPreviewTable previewTable(String storageSourceId, String tableName, int maxRows) {
    throw new UnsupportedOperationException("preview not supported for external datasets");
  }
}
