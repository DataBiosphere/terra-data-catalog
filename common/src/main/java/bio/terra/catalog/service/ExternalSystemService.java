package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.common.StorageSystemService;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.catalog.service.dataset.DatasetDao;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ExternalSystemService implements StorageSystemService {
  private final DatasetDao datasetDao;

  public ExternalSystemService(DatasetDao datasetDao) {
    this.datasetDao = datasetDao;
  }

  @Override
  public Map<String, StorageSystemInformation> getDatasets() {
    return datasetDao.listAllDatasets(StorageSystem.EXTERNAL).stream()
        .collect(
            Collectors.toMap(
                Dataset::storageSourceId,
                // For external datasets, the role is always DISCOVERER.
                dataset -> new StorageSystemInformation(DatasetAccessLevel.DISCOVERER)));
  }

  public StorageSystemInformation getDataset(String storageSourceId) {
    return new StorageSystemInformation(getRole(storageSourceId));
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
