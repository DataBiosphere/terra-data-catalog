package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.datarepo.RoleAndPhsId;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.DatasetPreviewTablesResponse;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.rawls.RawlsService;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.model.TableModel;
import bio.terra.rawls.model.Entity;
import bio.terra.rawls.model.EntityTypeMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  private final DatarepoService datarepoService;
  private final RawlsService rawlsService;
  private final SamService samService;
  private final DatasetDao datasetDao;
  private final ObjectMapper objectMapper;

  private static final int MAX_ROWS = 30;

  @Autowired
  public DatasetService(
      DatarepoService datarepoService,
      RawlsService rawlsService,
      SamService samService,
      DatasetDao datasetDao,
      ObjectMapper objectMapper) {
    this.datarepoService = datarepoService;
    this.rawlsService = rawlsService;
    this.samService = samService;
    this.datasetDao = datasetDao;
    this.objectMapper = objectMapper;
  }

  public static class IllegalMetadataException extends RuntimeException {
    public IllegalMetadataException(Throwable cause) {
      super(cause);
    }
  }

  private class DatasetWithAccessLevel {
    private final Dataset dataset;
    private final String phsId;
    private final DatasetAccessLevel accessLevel;

    // This is used for the get case where we don't care about access level
    public DatasetWithAccessLevel(Dataset dataset, String phsId) {
      this.dataset = dataset;
      this.phsId = phsId;
      this.accessLevel = null;
    }

    // This is used for the list case where we want to include access level
    public DatasetWithAccessLevel(Dataset dataset, String phsId, DatasetAccessLevel accessLevel) {
      this.dataset = dataset;
      this.phsId = phsId;
      this.accessLevel = accessLevel;
    }

    public Dataset getDataset() {
      return dataset;
    }

    public Object convertToObject() {
      ObjectNode node = toJsonNode(dataset.metadata());
      maybeSetNodeAccessUrlBasedOnPhsId(node);
      if (this.accessLevel != null) {
        node.set("accessLevel", TextNode.valueOf(String.valueOf(accessLevel)));
      }
      node.set("id", TextNode.valueOf(dataset.id().toValue()));
      return node;
    }

    private void maybeSetNodeAccessUrlBasedOnPhsId(ObjectNode node) {
      // TODO: Should this be DCAT:accessURL? How does the PHS ID URL interact with that field?
      if (phsId != null) {
        node.set("phsId", TextNode.valueOf(phsId));
        if (node.get("requestAccessUrl") == null) {
          node.set(
              "requestAccessUrl",
              TextNode.valueOf(
                  String.format(
                      "https://www.ncbi.nlm.nih.gov/projects/gap/cgi-bin/study.cgi?study_id=%s",
                      node.get("phsId").asText())));
        }
      }
    }
  }

  private ObjectNode toJsonNode(String json) {
    try {
      return objectMapper.readValue(json, ObjectNode.class);
    } catch (JsonProcessingException e) {
      // This shouldn't occur on retrieve, as the data stored in postgres must be valid JSON,
      // because it's stored as JSONB.
      throw new BadRequestException("catalogEntry/metadata must be a valid json object", e);
    }
  }

  private List<DatasetWithAccessLevel> convertTdrSnapshotsToDatasetsWithAccessLevel(
      Map<String, RoleAndPhsId> roleMap) {
    List<Dataset> datasets = datasetDao.find(StorageSystem.TERRA_DATA_REPO, roleMap.keySet());
    return datasets.stream()
        .map(
            dataset ->
                new DatasetWithAccessLevel(
                    dataset,
                    roleMap.get(dataset.storageSourceId()).getPhsId(),
                    roleMap.get(dataset.storageSourceId()).getDatasetAccessLevel()))
        .toList();
  }

  private List<DatasetWithAccessLevel> convertWorkspacesToDatasetsWithAccessLevel(
      Map<String, DatasetAccessLevel> roleMap) {
    List<Dataset> datasets = datasetDao.find(StorageSystem.TERRA_WORKSPACE, roleMap.keySet());
    return datasets.stream()
        .map(
            dataset ->
                new DatasetWithAccessLevel(dataset, null, roleMap.get(dataset.storageSourceId())))
        .toList();
  }

  private List<DatasetWithAccessLevel> collectDatarepoDatasets(AuthenticatedUserRequest user) {
    // For this storage system, get the collection of visible datasets and the user's roles for
    // each dataset.
    var roleMap = datarepoService.getSnapshotIdsAndRoles(user);
    return convertTdrSnapshotsToDatasetsWithAccessLevel(roleMap);
  }

  private List<DatasetWithAccessLevel> collectWorkspaceDatasets(AuthenticatedUserRequest user) {
    var roleMap = rawlsService.getWorkspaceIdsAndRoles(user);
    return convertWorkspacesToDatasetsWithAccessLevel(roleMap);
  }

  public DatasetsListResponse listDatasets(AuthenticatedUserRequest user) {
    List<DatasetWithAccessLevel> datasets = new ArrayList<>();
    datasets.addAll(collectWorkspaceDatasets(user));
    datasets.addAll(collectDatarepoDatasets(user));
    if (samService.hasGlobalAction(user, SamAction.READ_ANY_METADATA)) {
      datasets.addAll(
          datasetDao.listAllDatasets().stream()
              .map(dataset -> new DatasetWithAccessLevel(dataset, null, DatasetAccessLevel.READER))
              .filter(
                  datasetWithAccessLevel ->
                      !datasets.stream()
                          .map(datasetInList -> datasetInList.getDataset().id())
                          .toList()
                          .contains(datasetWithAccessLevel.getDataset().id()))
              .toList());
    }
    var response = new DatasetsListResponse();

    response.setResult(datasets.stream().map(DatasetWithAccessLevel::convertToObject).toList());
    return response;
  }

  private boolean checkStoragePermission(
      AuthenticatedUserRequest user, Dataset dataset, SamAction action) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> datarepoService
          .getRole(user, dataset.storageSourceId())
          .hasAction(action);
      case TERRA_WORKSPACE -> rawlsService
          .getRole(user, dataset.storageSourceId())
          .hasAction(action);
      case EXTERNAL -> false;
    };
  }

  private List<TableMetadata> generateDatasetTables(
      AuthenticatedUserRequest user, Dataset dataset) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> convertDatarepoTablesToCatalogTables(
          datarepoService.getPreviewTables(user, dataset.storageSourceId()).getTables());
      case TERRA_WORKSPACE -> convertRawlsTablesToCatalogTables(
          rawlsService.entityMetadata(user, dataset.storageSourceId()));
      case EXTERNAL -> List.of();
    };
  }

  private DatasetPreviewTable generateDatasetTablePreview(
      AuthenticatedUserRequest user, Dataset dataset, String tableName) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> generateDatarepoTable(user, dataset, tableName, MAX_ROWS);
      case TERRA_WORKSPACE -> generateRawlsTable(user, dataset, tableName, MAX_ROWS);
      case EXTERNAL -> new DatasetPreviewTable();
    };
  }

  private DatasetPreviewTable generateDatarepoTable(
      AuthenticatedUserRequest user, Dataset dataset, String tableName, int maxRows) {
    return new DatasetPreviewTable()
        .columns(
            datarepoService.getPreviewTables(user, dataset.storageSourceId()).getTables().stream()
                .filter(tableModel -> tableModel.getName().equals(tableName))
                .findFirst()
                .orElseThrow(
                    () ->
                        new NotFoundException(
                            String.format(
                                "Table %s is not found for dataset %s", tableName, dataset.id())))
                .getColumns()
                .stream()
                .map(DatasetService::convertDatarepoColumnModelToCatalogColumnModel)
                .toList())
        .rows(
            datarepoService
                .getPreviewTable(user, dataset.storageSourceId(), tableName, maxRows)
                .getResult());
  }

  private DatasetPreviewTable generateRawlsTable(
      AuthenticatedUserRequest user, Dataset dataset, String tableName, int maxRows) {
    Map<String, EntityTypeMetadata> entities =
        rawlsService.entityMetadata(user, dataset.storageSourceId());
    EntityTypeMetadata tableMetadata = entities.get(tableName);
    return new DatasetPreviewTable()
        .columns(convertTableMetadataToColumns(tableMetadata))
        .rows(
            rawlsService
                .entityQuery(user, dataset.storageSourceId(), tableName, maxRows)
                .getResults()
                .stream()
                .map(entity -> convertEntityToRow(entity, tableMetadata.getIdName()))
                .toList());
  }

  private Object convertEntityToRow(Entity entity, String idName) {
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

  private void ensureActionPermission(
      AuthenticatedUserRequest user, Dataset dataset, SamAction action) {
    // Ensure that the current user has permission to perform this action. The current user
    // can either have permission granted by the storage system that owns the dataset, or if
    // they're a catalog admin user who has permission to perform any operation on any
    // catalog entry.
    if (!samService.hasGlobalAction(user, action)
        && !checkStoragePermission(user, dataset, action)) {
      throw new UnauthorizedException(
          String.format("User %s does not have permission to %s", user.getEmail(), action));
    }
  }

  public void deleteMetadata(AuthenticatedUserRequest user, DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(user, dataset, SamAction.DELETE_ANY_METADATA);
    datasetDao.delete(dataset);
  }

  public String getMetadata(AuthenticatedUserRequest user, DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    String phsId = null;
    if (dataset.storageSystem().equals(StorageSystem.TERRA_DATA_REPO)) {
      phsId = datarepoService.getSnapshotPhsId(user, dataset.storageSourceId());
    }
    ensureActionPermission(user, dataset, SamAction.READ_ANY_METADATA);

    return new DatasetWithAccessLevel(dataset, phsId).convertToObject().toString();
  }

  public void updateMetadata(AuthenticatedUserRequest user, DatasetId datasetId, String metadata) {
    validateMetadata(metadata);
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(user, dataset, SamAction.UPDATE_ANY_METADATA);
    datasetDao.update(dataset.withMetadata(metadata));
  }

  public DatasetId createDataset(
      AuthenticatedUserRequest user,
      StorageSystem storageSystem,
      String storageSourceId,
      String metadata) {
    validateMetadata(metadata);
    var dataset = new Dataset(storageSourceId, storageSystem, metadata);
    ensureActionPermission(user, dataset, SamAction.CREATE_METADATA);
    return datasetDao.create(dataset).id();
  }

  public DatasetPreviewTablesResponse listDatasetPreviewTables(
      AuthenticatedUserRequest user, DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    var tableMetadataList = generateDatasetTables(user, dataset);
    return new DatasetPreviewTablesResponse().tables(tableMetadataList);
  }

  private void validateMetadata(String metadata) {
    toJsonNode(metadata);
  }

  private static List<TableMetadata> convertDatarepoTablesToCatalogTables(
      List<TableModel> datarepoTables) {
    return datarepoTables.stream()
        .map(
            tableModel ->
                new TableMetadata()
                    .name(tableModel.getName())
                    .hasData(tableModel.getRowCount() > 0))
        .toList();
  }

  private static List<TableMetadata> convertRawlsTablesToCatalogTables(
      Map<String, EntityTypeMetadata> entityTables) {
    return entityTables.entrySet().stream()
        .map(
            entry ->
                new TableMetadata().name(entry.getKey()).hasData(entry.getValue().getCount() != 0))
        .toList();
  }

  private static ColumnModel convertDatarepoColumnModelToCatalogColumnModel(
      bio.terra.datarepo.model.ColumnModel datarepoColumnModel) {
    return new ColumnModel().name(datarepoColumnModel.getName());
  }

  public DatasetPreviewTable getDatasetPreview(
      AuthenticatedUserRequest user, DatasetId datasetId, String tableName) {
    var dataset = datasetDao.retrieve(datasetId);
    return generateDatasetTablePreview(user, dataset, tableName);
  }

  public void exportDataset(AuthenticatedUserRequest user, DatasetId datasetId, UUID workspaceId) {
    var dataset = datasetDao.retrieve(datasetId);
    switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> datarepoService.exportSnapshot(
          user, dataset.storageSourceId(), workspaceId.toString());
      case TERRA_WORKSPACE -> rawlsService.exportWorkspaceDataset(
          user, dataset.storageSourceId(), workspaceId.toString());
      case EXTERNAL -> {
        /* NYI */
      }
    }
  }
}
