package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.EntitiesApi;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.EntityCopyDefinition;
import bio.terra.rawls.model.EntityQueryResponse;
import bio.terra.rawls.model.EntityTypeMetadata;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceName;
import bio.terra.rawls.model.WorkspaceResponse;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
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
  private final Client commonHttpClient;

  private static final Map<WorkspaceAccessLevel, DatasetAccessLevel> ROLE_TO_DATASET_ACCESS =
      Map.of(
          WorkspaceAccessLevel.PROJECT_OWNER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.OWNER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.WRITER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.READER, DatasetAccessLevel.READER,
          WorkspaceAccessLevel.NO_ACCESS, DatasetAccessLevel.DISCOVERER);

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

  public Map<String, DatasetAccessLevel> getWorkspaceIdsAndRoles(AuthenticatedUserRequest user) {
    try {
      return workspacesApi(user).listWorkspaces(ACCESS_LEVEL_AND_ID).stream()
          .collect(
              Collectors.toMap(
                  workspaceListResponse -> workspaceListResponse.getWorkspace().getWorkspaceId(),
                  workspaceListResponse ->
                      ROLE_TO_DATASET_ACCESS.get(workspaceListResponse.getAccessLevel())));
    } catch (ApiException e) {
      throw new RawlsException("List workspaces failed", e);
    }
  }

  public DatasetAccessLevel getRole(AuthenticatedUserRequest user, String workspaceId) {
    try {
      WorkspaceAccessLevel accessLevel =
          workspacesApi(user).getWorkspaceById(workspaceId, ACCESS_LEVEL).getAccessLevel();
      return ROLE_TO_DATASET_ACCESS.get(accessLevel);
    } catch (ApiException e) {
      throw new RawlsException("Get workspace role failed", e);
    }
  }

  WorkspacesApi workspacesApi(AuthenticatedUserRequest user) {
    return new WorkspacesApi(getApiClient(user));
  }

  public EntityQueryResponse entityQuery(
      AuthenticatedUserRequest user, String workspaceId, String tableName) {
    try {
      WorkspaceResponse response = workspacesApi(user).getWorkspaceById(workspaceId, List.of());
      return entitiesApi(user)
          .entityQuery(
              response.getWorkspace().getNamespace(),
              response.getWorkspace().getName(),
              tableName,
              null,
              BigDecimal.valueOf(30),
              null,
              null,
              null,
              null,
              List.of(),
              null,
              null);
    } catch (ApiException e) {
      throw new RawlsException("Entity Query failed for workspace %s".formatted(workspaceId), e);
    }
  }

  public Map<String, EntityTypeMetadata> entityMetadata(
      AuthenticatedUserRequest user, String workspaceId) {
    try {
      WorkspaceResponse response = workspacesApi(user).getWorkspaceById(workspaceId, List.of());
      return entitiesApi(user)
          .entityTypeMetadata(
              response.getWorkspace().getNamespace(),
              response.getWorkspace().getName(),
              true,
              null);
    } catch (ApiException e) {
      throw new RawlsException("Entity Metadata failed for workspace %s".formatted(workspaceId), e);
    }
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

  EntitiesApi entitiesApi(AuthenticatedUserRequest user) {
    return new EntitiesApi(getApiClient(user));
  }

  public static WorkspaceName getWorkspaceName(WorkspaceDetails workspaceDetails) {
    return new WorkspaceName()
        .namespace(workspaceDetails.getNamespace())
        .name(workspaceDetails.getName());
  }

  public void exportWorkspaceDataset(
      AuthenticatedUserRequest user, String workspaceIdSource, String workspaceIdDest) {
    try {
      // build source name
      WorkspaceDetails workspaceDetailsSource =
          workspacesApi(user).getWorkspaceById(workspaceIdSource, List.of()).getWorkspace();
      WorkspaceName workspaceNameSource = getWorkspaceName(workspaceDetailsSource);

      // build destination name
      WorkspaceDetails workspaceDetailsDest =
          workspacesApi(user).getWorkspaceById(workspaceIdDest, List.of()).getWorkspace();
      WorkspaceName workspaceNameDest = getWorkspaceName(workspaceDetailsDest);

      // possible bug: empty entityType and entityNames copies all entities
      EntityCopyDefinition body =
          new EntityCopyDefinition()
              .sourceWorkspace(workspaceNameSource)
              .destinationWorkspace(workspaceNameDest)
              .entityType("")
              .entityNames(List.of());
      entitiesApi(user).copyEntities(body, false);
    } catch (ApiException e) {
      String errorMsg =
          String.format(
              "Unable to export from workspace %s to workspace %s",
              workspaceIdSource, workspaceIdDest);
      throw new RawlsException(errorMsg, e);
    }
  }
}
