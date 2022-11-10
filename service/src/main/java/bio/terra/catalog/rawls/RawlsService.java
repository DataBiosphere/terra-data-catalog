package bio.terra.catalog.rawls;

import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.common.StorageSystemService;
import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.exception.NotFoundException;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.Entity;
import bio.terra.rawls.model.EntityCopyDefinition;
import bio.terra.rawls.model.EntityQueryResponse;
import bio.terra.rawls.model.EntityTypeMetadata;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceName;
import bio.terra.rawls.model.WorkspaceResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RawlsService implements StorageSystemService {
  private static final Logger logger = LoggerFactory.getLogger(RawlsService.class);
  public static final List<String> ACCESS_LEVEL = List.of("accessLevel");
  public static final List<String> ACCESS_LEVEL_AND_ID =
      List.of("accessLevel", "workspace.workspaceId");

  private final RawlsClient rawlsClient;

  private static final Map<WorkspaceAccessLevel, DatasetAccessLevel> ROLE_TO_DATASET_ACCESS =
      Map.of(
          WorkspaceAccessLevel.PROJECT_OWNER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.OWNER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.WRITER, DatasetAccessLevel.OWNER,
          WorkspaceAccessLevel.READER, DatasetAccessLevel.READER,
          WorkspaceAccessLevel.NO_ACCESS, DatasetAccessLevel.DISCOVERER);

  public RawlsService(RawlsClient rawlsClient) {
    this.rawlsClient = rawlsClient;
  }

  @Override
  public Map<String, StorageSystemInformation> getDatasets() {
    try {
      return rawlsClient.workspacesApi().listWorkspaces(ACCESS_LEVEL_AND_ID).stream()
          .collect(
              Collectors.toMap(
                  workspaceListResponse -> workspaceListResponse.getWorkspace().getWorkspaceId(),
                  workspaceListResponse ->
                      new StorageSystemInformation()
                          .datasetAccessLevel(
                              ROLE_TO_DATASET_ACCESS.get(workspaceListResponse.getAccessLevel()))));
    } catch (ApiException e) {
      throw new RawlsException("List workspaces failed", e);
    }
  }

  @Override
  public StorageSystemInformation getDataset(String workspaceId) {
    return new StorageSystemInformation().datasetAccessLevel(getRole(workspaceId));
  }

  @Override
  public DatasetAccessLevel getRole(String workspaceId) {
    try {
      WorkspaceAccessLevel accessLevel =
          rawlsClient.workspacesApi().getWorkspaceById(workspaceId, ACCESS_LEVEL).getAccessLevel();
      return ROLE_TO_DATASET_ACCESS.get(accessLevel);
    } catch (ApiException e) {
      throw new RawlsException("Get workspace role failed", e);
    }
  }

  private EntityQueryResponse entityQuery(String workspaceId, String tableName, int maxRows) {
    try {
      WorkspaceResponse response =
          rawlsClient.workspacesApi().getWorkspaceById(workspaceId, List.of());
      return rawlsClient
          .entitiesApi()
          .entityQuery(
              response.getWorkspace().getNamespace(),
              response.getWorkspace().getName(),
              tableName,
              null,
              BigDecimal.valueOf(maxRows),
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

  @Override
  public List<TableMetadata> getPreviewTables(String workspaceId) {
    return toCatalogTables(entityMetadata(workspaceId));
  }

  private Map<String, EntityTypeMetadata> entityMetadata(String workspaceId) {
    try {
      WorkspaceResponse response =
          rawlsClient.workspacesApi().getWorkspaceById(workspaceId, List.of());
      return rawlsClient
          .entitiesApi()
          .entityTypeMetadata(
              response.getWorkspace().getNamespace(),
              response.getWorkspace().getName(),
              true,
              null);
    } catch (ApiException e) {
      throw new RawlsException("Entity Metadata failed for workspace %s".formatted(workspaceId), e);
    }
  }

  @Override
  public SystemStatusSystems status() {
    var result = new SystemStatusSystems();
    try {
      // If the status is down then this method will throw
      rawlsClient.statusApi().systemStatus();
      result.ok(true);
    } catch (Exception e) {
      String errorMsg = "Rawls status check failed";
      logger.error(errorMsg, e);
      result.ok(false).addMessagesItem(errorMsg);
    }
    return result;
  }

  public static WorkspaceName getWorkspaceName(WorkspaceDetails workspaceDetails) {
    return new WorkspaceName()
        .namespace(workspaceDetails.getNamespace())
        .name(workspaceDetails.getName());
  }

  @Override
  public void exportToWorkspace(String workspaceIdSource, String workspaceIdDest) {
    try {
      // build source name
      WorkspaceDetails workspaceDetailsSource =
          rawlsClient.workspacesApi().getWorkspaceById(workspaceIdSource, List.of()).getWorkspace();
      WorkspaceName workspaceNameSource = getWorkspaceName(workspaceDetailsSource);

      // build destination name
      WorkspaceDetails workspaceDetailsDest =
          rawlsClient.workspacesApi().getWorkspaceById(workspaceIdDest, List.of()).getWorkspace();
      WorkspaceName workspaceNameDest = getWorkspaceName(workspaceDetailsDest);

      // possible bug: empty entityType and entityNames copies all entities
      EntityCopyDefinition body =
          new EntityCopyDefinition()
              .sourceWorkspace(workspaceNameSource)
              .destinationWorkspace(workspaceNameDest)
              .entityType("")
              .entityNames(List.of());
      rawlsClient.entitiesApi().copyEntities(body, false);
    } catch (ApiException e) {
      String errorMsg =
          String.format(
              "Unable to export from workspace %s to workspace %s",
              workspaceIdSource, workspaceIdDest);
      throw new RawlsException(errorMsg, e);
    }
  }

  @Override
  public DatasetPreviewTable previewTable(String storageSourceId, String tableName, int maxRows) {
    Map<String, EntityTypeMetadata> entities = entityMetadata(storageSourceId);
    EntityTypeMetadata tableMetadata = entities.get(tableName);
    if (tableMetadata == null) {
      throw new NotFoundException("Table %s not found for dataset".formatted(tableName));
    }
    return new DatasetPreviewTable()
        .columns(convertTableMetadataToColumns(tableMetadata))
        .rows(
            entityQuery(storageSourceId, tableName, maxRows).getResults().stream()
                .map(entity -> convertEntityToRow(entity, tableMetadata.getIdName()))
                .toList());
  }

  private static Object convertEntityToRow(Entity entity, String idName) {
    Map<String, Object> att = entity.getAttributes();
    Map<String, Object> rows = new HashMap<>(att);
    rows.put(idName, entity.getName());
    return rows;
  }

  private static List<ColumnModel> convertTableMetadataToColumns(EntityTypeMetadata entity) {
    List<ColumnModel> columns = new ArrayList<>();
    columns.add(new ColumnModel().name(entity.getIdName()));
    entity.getAttributeNames().stream()
        .map(name -> new ColumnModel().name(name))
        .forEach(columns::add);
    return columns;
  }

  private static List<TableMetadata> toCatalogTables(Map<String, EntityTypeMetadata> entityTables) {
    return entityTables.entrySet().stream()
        .map(
            entry ->
                new TableMetadata().name(entry.getKey()).hasData(entry.getValue().getCount() != 0))
        .toList();
  }
}
