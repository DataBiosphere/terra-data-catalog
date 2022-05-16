package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.common.iam.AuthenticatedUserRequest;
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
  public static final List<String> ACCESS_LEVEL_LIST = List.of("accessLevel");
  public static final List<String> ACCESS_LEVEL_AND_ID_LIST =
      List.of("accessLevel", "workspace.workspaceId");

  private final RawlsConfiguration rawlsConfig;
  private final Client commonHttpClient;
  private static final List<WorkspaceAccessLevel> OWNER_ROLES =
      List.of(WorkspaceAccessLevel.PROJECT_OWNER, WorkspaceAccessLevel.OWNER);
  private static final List<WorkspaceAccessLevel> WRITER_ROLES =
      List.of(
          WorkspaceAccessLevel.PROJECT_OWNER,
          WorkspaceAccessLevel.OWNER,
          WorkspaceAccessLevel.WRITER);
  private static final List<WorkspaceAccessLevel> READER_ROLES =
      List.of(
          WorkspaceAccessLevel.PROJECT_OWNER,
          WorkspaceAccessLevel.OWNER,
          WorkspaceAccessLevel.WRITER,
          WorkspaceAccessLevel.READER);

  @Autowired
  public RawlsService(RawlsConfiguration rawlsConfig) {
    this.rawlsConfig = rawlsConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  private ApiClient getApiClient(AuthenticatedUserRequest user) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(user.getToken());
    return apiClient;
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(rawlsConfig.basePath());
  }

  public Map<String, List<String>> getWorkspaceIdsAndRoles(AuthenticatedUserRequest user) {
    try {
      return workspacesApi(user).listWorkspaces(ACCESS_LEVEL_AND_ID_LIST).stream()
          .collect(
              Collectors.toMap(
                  workspaceListResponse -> workspaceListResponse.getWorkspace().getWorkspaceId(),
                  workspaceListResponse ->
                      List.of(workspaceListResponse.getAccessLevel().toString())));
    } catch (ApiException e) {
      throw new RawlsException("List workspaces failed", e);
    }
  }

  private List<WorkspaceAccessLevel> rolesForAction(SamAction action) {
    return switch (action) {
      case READ_ANY_METADATA -> READER_ROLES;
      case UPDATE_ANY_METADATA -> WRITER_ROLES;
      case CREATE_METADATA, DELETE_ANY_METADATA -> OWNER_ROLES;
    };
  }

  public boolean userHasAction(
      AuthenticatedUserRequest user, String workspaceId, SamAction action) {
    try {
      var roles = rolesForAction(action);
      return roles.contains(
          workspacesApi(user).getWorkspaceById(workspaceId, ACCESS_LEVEL_LIST).getAccessLevel());
    } catch (ApiException e) {
      throw new RawlsException("Get workspace roles failed", e);
    }
  }

  WorkspacesApi workspacesApi(AuthenticatedUserRequest user) {
    return new WorkspacesApi(getApiClient(user));
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
