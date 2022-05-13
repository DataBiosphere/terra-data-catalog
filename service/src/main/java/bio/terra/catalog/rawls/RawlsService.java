package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.WorkspaceListResponse;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RawlsService {
  private static final Logger logger = LoggerFactory.getLogger(RawlsService.class);
  public static final String PROJECT_OWNER_ROLE_NAME = "project-owner";
  public static final String OWNER_ROLE_NAME = "owner";
  public static final String WRITER_ROLE_NAME = "writer";
  public static final String READER_ROLE_NAME = "reader";

  private final RawlsConfiguration rawlsConfig;
  private final Client commonHttpClient;
  private static final List<String> OWNER_ROLES = List.of(PROJECT_OWNER_ROLE_NAME, OWNER_ROLE_NAME);
  private static final List<String> WRITER_ROLES =
      List.of(PROJECT_OWNER_ROLE_NAME, OWNER_ROLE_NAME, WRITER_ROLE_NAME);
  private static final List<String> READER_ROLES =
      List.of(PROJECT_OWNER_ROLE_NAME, OWNER_ROLE_NAME, WRITER_ROLE_NAME, READER_ROLE_NAME);

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

  public List<WorkspaceListResponse> getWorkspaceIdsAndRoles(AuthenticatedUserRequest user) {
    try {
      return workspacesApi(user).listWorkspaces(List.of("accessLevel", "workspace.workspaceId"));
    } catch (ApiException e) {
      throw new RawlsException("Enumerate snapshots failed", e);
    }
  }

  private List<String> rolesForAction(SamAction action) {
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
          workspacesApi(user)
              .getWorkspaceById(workspaceId, List.of("accessLevel"))
              .getAccessLevel()
              .getValue());
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
