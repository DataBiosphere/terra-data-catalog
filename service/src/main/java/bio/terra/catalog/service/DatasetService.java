package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
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
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  private final DatarepoService datarepoService;
  public final RawlsService rawlsService;
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

  private record DatasetWithAccessLevel(Dataset dataset, DatasetAccessLevel accessLevel) {}

  public Object convertDatasetToObject(DatasetWithAccessLevel dataset) {
    ObjectNode node = toJsonNode(dataset.dataset.metadata());
    node.set("accessLevel", TextNode.valueOf(String.valueOf(dataset.accessLevel)));
    node.set("id", TextNode.valueOf(dataset.dataset.id().toValue()));
    return node;
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

  private List<DatasetWithAccessLevel> convertSourceObjectsToDatasetsWithAccessLevel(
      Map<String, DatasetAccessLevel> roleMap, StorageSystem storageSystem) {
    List<Dataset> datasets = datasetDao.find(storageSystem, roleMap.keySet());
    return datasets.stream()
        .map(dataset -> new DatasetWithAccessLevel(dataset, roleMap.get(dataset.storageSourceId())))
        .toList();
  }

  private List<DatasetWithAccessLevel> collectDatarepoDatasets(AuthenticatedUserRequest user) {
    // For this storage system, get the collection of visible datasets and the user's roles for
    // each dataset.
    var roleMap = datarepoService.getSnapshotIdsAndRoles(user);
    return convertSourceObjectsToDatasetsWithAccessLevel(roleMap, StorageSystem.TERRA_DATA_REPO);
  }

  private List<DatasetWithAccessLevel> collectWorkspaceDatasets(AuthenticatedUserRequest user) {
    var roleMap = rawlsService.getWorkspaceIdsAndRoles(user);
    return convertSourceObjectsToDatasetsWithAccessLevel(roleMap, StorageSystem.TERRA_WORKSPACE);
  }

  public DatasetsListResponse listDatasets(AuthenticatedUserRequest user) {
    List<DatasetWithAccessLevel> datasets = new ArrayList<>();
    datasets.addAll(collectWorkspaceDatasets(user));
    datasets.addAll(collectDatarepoDatasets(user));
    if (samService.hasGlobalAction(user, SamAction.READ_ANY_METADATA)) {
      datasets.addAll(
          datasetDao.listAllDatasets().stream()
              .map(dataset -> new DatasetWithAccessLevel(dataset, DatasetAccessLevel.READER))
              .filter(
                  datasetWithAccessLevel ->
                      !datasets.stream()
                          .map(datasetInList -> datasetInList.dataset.id())
                          .toList()
                          .contains(datasetWithAccessLevel.dataset.id()))
              .toList());
    }
    var response = new DatasetsListResponse();

    response.setResult(datasets.stream().map(this::convertDatasetToObject).toList());
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
      case TERRA_DATA_REPO -> datarepoService.getPreviewTables(user, dataset.storageSourceId());
      case TERRA_WORKSPACE -> rawlsService.getPreviewTables(user, dataset.storageSourceId());
      case EXTERNAL -> List.of();
    };
  }

  private DatasetPreviewTable generateDatasetTablePreview(
      AuthenticatedUserRequest user, Dataset dataset, String tableName) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> datarepoService.previewTable(user, dataset, tableName, MAX_ROWS);
      case TERRA_WORKSPACE -> rawlsService.previewTable(user, dataset, tableName, MAX_ROWS);
      case EXTERNAL -> new DatasetPreviewTable();
    };
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
    ensureActionPermission(user, dataset, SamAction.READ_ANY_METADATA);
    return dataset.metadata();
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
