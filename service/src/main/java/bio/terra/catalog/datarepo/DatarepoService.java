package bio.terra.catalog.datarepo;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.SnapshotPreviewModel;
import bio.terra.datarepo.model.SnapshotRetrieveIncludeModel;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;
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

  public static final Map<String, DatasetAccessLevel> SNAPSHOT_ROLE_TO_DATASET_ACCESS_LEVEL =
      Map.of(
          ADMIN_ROLE_NAME, DatasetAccessLevel.OWNER,
          STEWARD_ROLE_NAME, DatasetAccessLevel.OWNER,
          READER_ROLE_NAME, DatasetAccessLevel.READER,
          DISCOVERER_ROLE_NAME, DatasetAccessLevel.DISCOVERER);

  // This is the maximum number of datasets returned. If we have more than this number of datasets
  // in TDR that are in the catalog, this number will need to be increased.
  private static final int MAX_DATASETS = 1000;

  private final DatarepoConfiguration datarepoConfig;
  private final Client commonHttpClient;

  @Autowired
  public DatarepoService(DatarepoConfiguration datarepoConfig) {
    this.datarepoConfig = datarepoConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  public Map<String, List<DatasetAccessLevel>> getSnapshotIdsAndRoles(
      AuthenticatedUserRequest user) {
    try {
      Map<String, List<String>> response =
          snapshotsApi(user)
              .enumerateSnapshots(null, MAX_DATASETS, null, null, null, null, null)
              .getRoleMap();
      return response.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  entry ->
                      entry.getValue().stream()
                          .map(SNAPSHOT_ROLE_TO_DATASET_ACCESS_LEVEL::get)
                          .toList()));
    } catch (ApiException e) {
      throw new DatarepoException("Enumerate snapshots failed", e);
    }
  }

  public SnapshotModel getPreviewTables(AuthenticatedUserRequest user, String snapshotId) {
    try {
      UUID id = UUID.fromString(snapshotId);
      return snapshotsApi(user).retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES));
    } catch (ApiException e) {
      throw new DatarepoException(e);
    }
  }

  public SnapshotPreviewModel getPreviewTable(
      AuthenticatedUserRequest user, String snapshotId, String tableName) {
    try {
      UUID id = UUID.fromString(snapshotId);
      return snapshotsApi(user).lookupSnapshotPreviewById(id, tableName, null, null);
    } catch (ApiException e) {
      throw new DatarepoException(e);
    }
  }

  public DatasetAccessLevel getRole(AuthenticatedUserRequest user, String snapshotId) {
    try {
      UUID id = UUID.fromString(snapshotId);
      List<String> roles = snapshotsApi(user).retrieveUserSnapshotRoles(id);
      for (DatasetAccessLevel datasetAccessLevel : DatasetAccessLevel.values()) {
        if (roles.stream()
            .map(SNAPSHOT_ROLE_TO_DATASET_ACCESS_LEVEL::get)
            .anyMatch(
                roleAsDatasetAccessLevel -> roleAsDatasetAccessLevel.equals(datasetAccessLevel))) {
          return datasetAccessLevel;
        }
      }
      // If users can see the snapshot, but somehow don't match any of the known roles,
      // we grant our lowest possible access level that people who can see the snapshot can have
      return DatasetAccessLevel.DISCOVERER;
    } catch (ApiException e) {
      throw new DatarepoException("Get snapshot roles failed", e);
    }
  }

  @VisibleForTesting
  SnapshotsApi snapshotsApi(AuthenticatedUserRequest user) {
    return new SnapshotsApi(getApiClient(user));
  }

  private ApiClient getApiClient(AuthenticatedUserRequest user) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(user.getToken());
    return apiClient;
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(datarepoConfig.basePath());
  }

  @VisibleForTesting
  UnauthenticatedApi unauthenticatedApi() {
    return new UnauthenticatedApi(getApiClient());
  }

  public SystemStatusSystems status() {
    var result = new SystemStatusSystems();
    try {
      // Don't retry status check
      RepositoryStatusModel status = unauthenticatedApi().serviceStatus();
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
}
