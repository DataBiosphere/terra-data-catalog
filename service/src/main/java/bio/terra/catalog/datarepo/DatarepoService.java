package bio.terra.catalog.datarepo;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.RepositoryStatusModel;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatarepoService {
  private static final Logger logger = LoggerFactory.getLogger(DatarepoService.class);
  public static final String ADMIN_ROLE_NAME = "admin";
  public static final String CUSTODIAN_ROLE_NAME = "custodian";
  public static final String READER_ROLE_NAME = "reader";
  public static final String DISCOVERER_ROLE_NAME = "discoverer";

  private static final List<String> OWNER_ROLES = List.of(ADMIN_ROLE_NAME, CUSTODIAN_ROLE_NAME);
  private static final List<String> READER_ROLES =
      List.of(ADMIN_ROLE_NAME, CUSTODIAN_ROLE_NAME, READER_ROLE_NAME, DISCOVERER_ROLE_NAME);

  private final DatarepoConfiguration datarepoConfig;
  private final SamAuthenticatedUserRequestFactory userFactory;
  private final Client commonHttpClient;

  @Autowired
  public DatarepoService(
      DatarepoConfiguration datarepoConfig, SamAuthenticatedUserRequestFactory userFactory) {
    this.datarepoConfig = datarepoConfig;
    this.userFactory = userFactory;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  public Map<String, List<String>> getSnapshotIdsAndRoles() {
    try {
      return snapshotsApi()
          .enumerateSnapshots(null, null, null, null, null, null, null)
          .getRoleMap();
    } catch (ApiException e) {
      throw new DatarepoException("Enumerate snapshots failed", e);
    }
  }

  private List<String> rolesForAction(SamAction action) {
    return switch (action) {
      case READ_ANY_METADATA -> READER_ROLES;
      case CREATE_METADATA, DELETE_ANY_METADATA, UPDATE_ANY_METADATA -> OWNER_ROLES;
    };
  }

  public boolean userHasAction(String snapshotId, SamAction action) {
    try {
      UUID id = UUID.fromString(snapshotId);
      var roles = rolesForAction(action);
      return snapshotsApi().retrieveUserSnapshotRoles(id).stream().anyMatch(roles::contains);
    } catch (ApiException e) {
      throw new DatarepoException("Get snapshot roles failed", e);
    }
  }

  @VisibleForTesting
  SnapshotsApi snapshotsApi() {
    return new SnapshotsApi(getApiClient(userFactory.getUser().getToken()));
  }

  private ApiClient getApiClient(String token) {
    var apiClient =
        new ApiClient().setHttpClient(commonHttpClient).setBasePath(datarepoConfig.basePath());
    if (token != null) {
      apiClient.setAccessToken(token);
    }
    return apiClient;
  }

  @VisibleForTesting
  UnauthenticatedApi unauthenticatedApi() {
    return new UnauthenticatedApi(getApiClient(null));
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
