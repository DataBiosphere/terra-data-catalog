package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.DatasetPreviewMetadataResponse;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.model.DatePartitionOptionsModel;
import bio.terra.catalog.model.IntPartitionOptionsModel;
import bio.terra.catalog.model.TableDataType;
import bio.terra.catalog.model.TableModel;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
      for (String role : roleMap.get(dataset.storageSourceId())) {
        roles.add(TextNode.valueOf(role));
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

  public DatasetPreviewMetadataResponse getDatasetPreviewMetadata(
      AuthenticatedUserRequest user, DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(user, dataset, SamAction.READ_ANY_METADATA);
    // Right now, this code makes an assumption that all datasets are snapshots.
    // Eventually this will not be true and this should change.
    var snapshotMetadata = datarepoService.getPreviewMetadata(user, dataset.storageSourceId());
    return new DatasetPreviewMetadataResponse()
        .id(snapshotMetadata.getId())
        .dataProject(snapshotMetadata.getDataProject())
        .tables(convertDatarepoTablesToCatalogTables(snapshotMetadata.getTables()));
  }

  private List<TableModel> convertDatarepoTablesToCatalogTables(
      List<bio.terra.datarepo.model.TableModel> datarepoTables) {
    return Optional.ofNullable(datarepoTables)
        .map(
            tableModels ->
                tableModels.stream()
                    .map(
                        tableModel ->
                            new TableModel()
                                .name(tableModel.getName())
                                .columns(
                                    convertDataRepoColumnsToCatalogColumns(tableModel.getColumns()))
                                .primaryKey(tableModel.getPrimaryKey())
                                .partitionMode(
                                    convertDatarepoPartiotionMode(tableModel.getPartitionMode()))
                                .datePartitionOptions(
                                    convertDatarepoDatePartionsOptionsModel(
                                        tableModel.getDatePartitionOptions()))
                                .intPartitionOptions(
                                    convertDatarepoIntPartionsOptionsModel(
                                        tableModel.getIntPartitionOptions()))
                                .rowCount(tableModel.getRowCount()))
                    .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  private TableModel.PartitionModeEnum convertDatarepoPartiotionMode(
      bio.terra.datarepo.model.TableModel.PartitionModeEnum datarepoPartitionModeEnum) {
    return TableModel.PartitionModeEnum.fromValue(
        Optional.ofNullable(datarepoPartitionModeEnum)
            .map(bio.terra.datarepo.model.TableModel.PartitionModeEnum::getValue)
            .orElse(TableModel.PartitionModeEnum.NONE.toString()));
  }

  private DatePartitionOptionsModel convertDatarepoDatePartionsOptionsModel(
      bio.terra.datarepo.model.DatePartitionOptionsModel datarepoDatePartionsOptionsModel) {
    return Optional.ofNullable(datarepoDatePartionsOptionsModel)
        .map(
            datePartitionOptionsModel ->
                new DatePartitionOptionsModel().column(datePartitionOptionsModel.getColumn()))
        .orElse(null);
  }

  private IntPartitionOptionsModel convertDatarepoIntPartionsOptionsModel(
      bio.terra.datarepo.model.IntPartitionOptionsModel datarepoIntPartitionsOptionsModel) {
    return Optional.ofNullable(datarepoIntPartitionsOptionsModel)
        .map(
            intPartitionOptionsModel ->
                new IntPartitionOptionsModel()
                    .column(intPartitionOptionsModel.getColumn())
                    .min(intPartitionOptionsModel.getMin())
                    .max(intPartitionOptionsModel.getMax())
                    .interval(intPartitionOptionsModel.getInterval()))
        .orElse(null);
  }

  private List<ColumnModel> convertDataRepoColumnsToCatalogColumns(
      List<bio.terra.datarepo.model.ColumnModel> datarepoColumns) {
    return Optional.ofNullable(datarepoColumns)
        .map(
            columnModels ->
                columnModels.stream()
                    .map(
                        columnModel ->
                            new ColumnModel()
                                .name(columnModel.getName())
                                .datatype(
                                    TableDataType.fromValue(columnModel.getDatatype().getValue()))
                                .arrayOf(columnModel.isArrayOf()))
                    .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }
}
