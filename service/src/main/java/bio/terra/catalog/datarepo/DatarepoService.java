package bio.terra.catalog.datarepo;

import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.SnapshotPreviewModel;
import bio.terra.datarepo.model.SnapshotRetrieveIncludeModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatarepoService {
  private static final Logger logger = LoggerFactory.getLogger(DatarepoService.class);
  public static final String ADMIN_ROLE_NAME = "admin";
  public static final String STEWARD_ROLE_NAME = "steward";
  public static final String READER_ROLE_NAME = "reader";
  public static final String DISCOVERER_ROLE_NAME = "discoverer";

  private static final Map<String, DatasetAccessLevel> ROLE_TO_DATASET_ACCESS =
      Map.of(
          ADMIN_ROLE_NAME, DatasetAccessLevel.OWNER,
          STEWARD_ROLE_NAME, DatasetAccessLevel.OWNER,
          READER_ROLE_NAME, DatasetAccessLevel.READER,
          DISCOVERER_ROLE_NAME, DatasetAccessLevel.DISCOVERER);

  // This is the maximum number of datasets returned. If we have more than this number of datasets
  // in TDR that are in the catalog, this number will need to be increased.
  private static final int MAX_DATASETS = 1000;

  private final DatarepoClient datarepoClient;

  @Autowired
  public DatarepoService(DatarepoClient datarepoClient) {
    this.datarepoClient = datarepoClient;
  }

  private DatasetAccessLevel getHighestAccessFromRoleList(List<String> roles) {
    for (DatasetAccessLevel datasetAccessLevel : DatasetAccessLevel.values()) {
      if (roles.stream()
          .map(ROLE_TO_DATASET_ACCESS::get)
          .anyMatch(
              roleAsDatasetAccessLevel -> roleAsDatasetAccessLevel.equals(datasetAccessLevel))) {
        return datasetAccessLevel;
      }
    }
    return DatasetAccessLevel.NO_ACCESS;
  }

  public Map<String, DatasetAccessLevel> getSnapshotIdsAndRoles(AuthenticatedUserRequest user) {
    try {
      Map<String, List<String>> response =
          datarepoClient
              .snapshotsApi(user)
              .enumerateSnapshots(null, MAX_DATASETS, null, null, null, null, null)
              .getRoleMap();
      return response.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey, entry -> getHighestAccessFromRoleList(entry.getValue())));
    } catch (ApiException e) {
      throw new DatarepoException("Enumerate snapshots failed", e);
    }
  }

  private SnapshotModel getSnapshotTables(AuthenticatedUserRequest user, String snapshotId) {
    try {
      UUID id = UUID.fromString(snapshotId);
      return datarepoClient
          .snapshotsApi(user)
          .retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES));
    } catch (ApiException e) {
      throw new DatarepoException(e);
    }
  }

  public List<TableMetadata> getPreviewTables(AuthenticatedUserRequest user, String snapshotId) {
    return getSnapshotTables(user, snapshotId).getTables().stream()
        .map(table -> new TableMetadata().name(table.getName()).hasData(table.getRowCount() > 0))
        .toList();
  }

  public DatasetPreviewTable previewTable(
      AuthenticatedUserRequest user, Dataset dataset, String tableName, int maxRows) {
    return new DatasetPreviewTable()
        .columns(
            getSnapshotTables(user, dataset.storageSourceId()).getTables().stream()
                .filter(tableModel -> tableModel.getName().equals(tableName))
                .findFirst()
                .orElseThrow(
                    () ->
                        new NotFoundException(
                            String.format(
                                "Table %s is not found for dataset %s", tableName, dataset.id())))
                .getColumns()
                .stream()
                .map(column -> new ColumnModel().name(column.getName()))
                .toList())
        .rows(getPreviewTable(user, dataset.storageSourceId(), tableName, maxRows).getResult());
  }

  @VisibleForTesting
  SnapshotPreviewModel getPreviewTable(
      AuthenticatedUserRequest user, String snapshotId, String tableName, int maxRows) {
    try {
      UUID id = UUID.fromString(snapshotId);
      return datarepoClient
          .snapshotsApi(user)
          .lookupSnapshotPreviewById(id, tableName, null, maxRows, null, null);
    } catch (ApiException e) {
      throw new DatarepoException(e);
    }
  }

  public DatasetAccessLevel getRole(AuthenticatedUserRequest user, String snapshotId) {
    try {
      UUID id = UUID.fromString(snapshotId);
      List<String> roles = datarepoClient.snapshotsApi(user).retrieveUserSnapshotRoles(id);
      return getHighestAccessFromRoleList(roles);
    } catch (ApiException e) {
      throw new DatarepoException("Get snapshot roles failed", e);
    }
  }

  public SystemStatusSystems status() {
    var result = new SystemStatusSystems();
    try {
      // Don't retry status check
      RepositoryStatusModel status = datarepoClient.unauthenticatedApi().serviceStatus();
      result.ok(status.isOk());
      // Populate error message if system status is non-ok
      if (!result.isOk()) {
        String errorMsg = "Data repo status check failed. Messages = " + status.getSystems();
        logger.error(errorMsg);
        result.addMessagesItem(errorMsg);
      }
    } catch (Exception e) {
      String errorMsg = "Data repo status check failed";
      logger.error(errorMsg, e);
      result.ok(false).addMessagesItem(errorMsg);
    }
    return result;
  }

  public void exportSnapshot(
      AuthenticatedUserRequest user, String snapshotIdSource, String workspaceIdDest) {
    throw new BadRequestException("Exporting Data Repo datasets is not supported in the service");
  }
}
