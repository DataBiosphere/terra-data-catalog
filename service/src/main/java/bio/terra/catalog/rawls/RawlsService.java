package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RawlsService {
  private static final Logger logger = LoggerFactory.getLogger(RawlsService.class);
  public static final List<String> ACCESS_LEVEL = List.of("accessLevel");
  public static final List<String> ACCESS_LEVEL_AND_ID =
      List.of("accessLevel", "workspace.workspaceId");

  private final RawlsConfiguration rawlsConfig;
  private final SamAuthenticatedUserRequestFactory userFactory;
  private final Client commonHttpClient;

  private static final Map<WorkspaceAccessLevel, DatasetAccessLevel> ROLE_TO_DATASET_ACCESS =
      Map.of(
          WorkspaceAccessLevel.PROJECT_OWNER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.OWNER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.WRITER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.READER, DatasetAccessLevel.READER,
          WorkspaceAccessLevel.NO_ACCESS, DatasetAccessLevel.DISCOVERER);

  @Autowired
  public RawlsService(
      RawlsConfiguration rawlsConfig, SamAuthenticatedUserRequestFactory userFactory) {
    this.rawlsConfig = rawlsConfig;
    this.userFactory = userFactory;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(rawlsConfig.basePath());
  }

  public Map<String, DatasetAccessLevel> getWorkspaceIdsAndRoles() {
    try {
      return workspacesApi().listWorkspaces(ACCESS_LEVEL_AND_ID).stream()
          .collect(
              Collectors.toMap(
                  workspaceListResponse -> workspaceListResponse.getWorkspace().getWorkspaceId(),
                  workspaceListResponse ->
                      ROLE_TO_DATASET_ACCESS.get(workspaceListResponse.getAccessLevel())));
    } catch (ApiException e) {
      throw new RawlsException("List workspaces failed", e);
    }
  }

  public DatasetAccessLevel getRole(String workspaceId) {
    try {
      WorkspaceAccessLevel accessLevel =
          workspacesApi().getWorkspaceById(workspaceId, ACCESS_LEVEL).getAccessLevel();
      return ROLE_TO_DATASET_ACCESS.get(accessLevel);
    } catch (ApiException e) {
      throw new RawlsException("Get workspace role failed", e);
    }
  }

  WorkspacesApi workspacesApi() {
    return new WorkspacesApi(getApiClient(userFactory.getUser().getToken()));
  }

  @VisibleForTesting
  StatusApi statusApi() {
    return new StatusApi(getApiClient());
  }

  public SystemStatusSystems status() {
    var result = new SystemStatusSystems();
    try {
      // If the status is down then this method will throw
      statusApi().systemStatus();
      result.ok(true);
    } catch (Exception e) {
      String errorMsg = "Rawls status check failed";
      logger.error(errorMsg, e);
      result.ok(false).addMessagesItem(errorMsg);
    }
    return result;
  }
}
