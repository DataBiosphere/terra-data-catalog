package bio.terra.catalog.datarepo;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatarepoService {
  private static final Logger logger = LoggerFactory.getLogger(DatarepoService.class);
  public static final String OWNER_ROLE_NAME = "admin";
  private final DatarepoConfiguration datarepoConfig;
  private final Client commonHttpClient;

  @Autowired
  public DatarepoService(DatarepoConfiguration datarepoConfig) {
    this.datarepoConfig = datarepoConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  public List<SnapshotSummaryModel> getSnapshots(AuthenticatedUserRequest user) {
    try {
      return snapshotsApi(user)
          .enumerateSnapshots(null, null, null, null, null, null, null)
          .getItems();
    } catch (ApiException e) {
      throw new DatarepoException("Enumerate snapshots failed", e);
    }
  }

  public boolean isOwner(AuthenticatedUserRequest user, String snapshotId) {
    try {
      UUID id = UUID.fromString(snapshotId);
      return snapshotsApi(user).retrieveUserSnapshotRoles(id).contains(OWNER_ROLE_NAME);
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

  public SystemStatusSystems status() {
    // No access token needed since this is an unauthenticated API.
    UnauthenticatedApi api = new UnauthenticatedApi(getApiClient());
    var result = new SystemStatusSystems();
    try {
      // Don't retry status check
      RepositoryStatusModel status = api.serviceStatus();
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
