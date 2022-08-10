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
import bio.terra.rawls.model.EntityQueryResponse;
import bio.terra.rawls.model.EntityTypeMetadata;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceResponse;
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

  //  public List<Entity> entityList(AuthenticatedUserRequest user, String storageSourceId)  {
  //    WorkspaceResponse response = null;
  //    try {
  //      response = workspacesApi(user).getWorkspaceById(storageSourceId, List.of());
  //      var entities = entitiesApi(user).listEntities(response.getWorkspace().getNamespace(),
  // response.getWorkspace().getName(), Sample);
  //      return entities;
  //    } catch (ApiException e) {
  //      throw new RuntimeException(e);
  //    }
  //  }

  public EntityQueryResponse entityQuery(
      AuthenticatedUserRequest user, String storageSourceId, String tableName) {
    WorkspaceResponse response;
    try {
      response = workspacesApi(user).getWorkspaceById(storageSourceId, List.of());
      return entitiesApi(user)
          .entityQuery(
              response.getWorkspace().getNamespace(),
              response.getWorkspace().getName(),
              tableName,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              null,
              null);
    } catch (ApiException e) {
      throw new RawlsException("Entity Query Failed", e);
    }
  }

  public Map<String, EntityTypeMetadata> entityMetadata(
      AuthenticatedUserRequest user, String storageSourceId) {
    WorkspaceResponse response;
    try {
      response = workspacesApi(user).getWorkspaceById(storageSourceId, List.of());
      var entities =
          entitiesApi(user)
              .entityTypeMetadata(
                  response.getWorkspace().getNamespace(),
                  response.getWorkspace().getName(),
                  true,
                  null);
      //noinspection unchecked
      return ((Map<String, Map<String, Object>>) entities)
          .entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      entry -> {
                        Map<String, Object> value = entry.getValue();
                        //noinspection unchecked
                        return new EntityTypeMetadata()
                            .idName((String) value.get("idName"))
                            .count((Integer) value.get("count"))
                            .attributeNames((List<String>) value.get("attributeNames"));
                      }));
    } catch (ApiException e) {
      throw new RawlsException("Entity Metadata failed", e);
    }
  }

  EntitiesApi entitiesApi(AuthenticatedUserRequest user) {
    return new EntitiesApi(getApiClient(user));
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
