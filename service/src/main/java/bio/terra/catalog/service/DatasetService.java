package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.DatasetAccessLevel;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.DatasetPreviewTablesResponse;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.model.TableModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  private final DatarepoService datarepoService;
  private final SamService samService;
  private final DatasetDao datasetDao;
  private final ObjectMapper objectMapper;

  @Autowired
  public DatasetService(
      DatarepoService datarepoService,
      SamService samService,
      DatasetDao datasetDao,
      ObjectMapper objectMapper) {
    this.datarepoService = datarepoService;
    this.samService = samService;
    this.datasetDao = datasetDao;
    this.objectMapper = objectMapper;
  }

  public static class IllegalMetadataException extends RuntimeException {
    public IllegalMetadataException(Throwable cause) {
      super(cause);
    }
  }

  private ObjectNode toJsonNode(String json) {
    try {
      return objectMapper.readValue(json, ObjectNode.class);
    } catch (JsonProcessingException e) {
      // This shouldn't occur, as the data stored in postgres must be valid JSON, because it's
      // stored as JSONB.
      throw new IllegalMetadataException(e);
    }
  }

  private void collectDatarepoDatasets(
      AuthenticatedUserRequest user, DatasetsListResponse response) {
    // For this storage system, get the collection of visible datasets and the user's roles for
    // each dataset.
    var roleMap = datarepoService.getSnapshotIdsAndRoles(user);

    // Using the storage system's source IDs, look up the metadata for each of these datasets.
    List<Dataset> datasets = datasetDao.find(StorageSystem.TERRA_DATA_REPO, roleMap.keySet());

    // Merge the permission (role) data into the metadata results.
    for (Dataset dataset : datasets) {
      ArrayNode roles = objectMapper.createArrayNode();
      for (DatasetAccessLevel role : roleMap.get(dataset.storageSourceId())) {
        roles.add(TextNode.valueOf(role.toString()));
      }
      ObjectNode node = toJsonNode(dataset.metadata());
      node.set("roles", roles);
      node.set("id", TextNode.valueOf(dataset.id().toValue()));
      response.addResultItem(node);
    }
  }

  public DatasetsListResponse listDatasets(AuthenticatedUserRequest user) {
    var response = new DatasetsListResponse();
    collectDatarepoDatasets(user, response);
    return response;
  }

  private boolean checkStoragePermission(
      AuthenticatedUserRequest user, Dataset dataset, SamAction action) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> datarepoService.userHasAction(
          user, dataset.storageSourceId(), action);
      case TERRA_WORKSPACE, EXTERNAL -> false;
    };
  }

  private List<TableMetadata> generateDatasetTables(
      AuthenticatedUserRequest user, Dataset dataset) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> convertDatarepoTablesToCatalogTables(
          datarepoService.getPreviewTables(user, dataset.storageSourceId()).getTables());
      case TERRA_WORKSPACE, EXTERNAL -> List.of();
    };
  }

  private DatasetPreviewTable generateDatasetTablePreview(
      AuthenticatedUserRequest user, Dataset dataset, String tableName) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> generateDatarepoTable(user, dataset, tableName);
      case TERRA_WORKSPACE, EXTERNAL -> new DatasetPreviewTable();
    };
  }

  private DatasetPreviewTable generateDatarepoTable(
      AuthenticatedUserRequest user, Dataset dataset, String tableName) {
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
                .getPreviewTable(user, dataset.storageSourceId(), tableName)
                .getResult());
  }

  private void ensureActionPermission(
      AuthenticatedUserRequest user, Dataset dataset, SamAction action) {
    // Ensure that the current user has permission to perform this action. The current user
    // can either have permission granted by the storage system that owns the dataset, or if
    // they're a catalog admin user who has permission to perform any operation on any
    // catalog entry.
    if (!checkStoragePermission(user, dataset, action)
        && !samService.hasGlobalAction(user, action)) {
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
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(user, dataset, SamAction.UPDATE_ANY_METADATA);
    datasetDao.update(dataset.withMetadata(metadata));
  }

  public DatasetId createDataset(
      AuthenticatedUserRequest user,
      StorageSystem storageSystem,
      String storageSourceId,
      String metadata) {
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

  private static ColumnModel convertDatarepoColumnModelToCatalogColumnModel(
      bio.terra.datarepo.model.ColumnModel datarepoColumnModel) {
    return new ColumnModel()
        .name(datarepoColumnModel.getName())
        .arrayOf(datarepoColumnModel.isArrayOf());
  }

  public DatasetPreviewTable getDatasetPreview(
      AuthenticatedUserRequest user, DatasetId datasetId, String tableName) {
    var dataset = datasetDao.retrieve(datasetId);
    return generateDatasetTablePreview(user, dataset, tableName);
  }
}
