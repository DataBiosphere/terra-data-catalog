package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

  public DatasetsListResponse listDatasets() {
    var response = new DatasetsListResponse();
    for (SnapshotSummaryModel model : datarepoService.getSnapshots()) {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("id", model.getId().toString());
      node.put("dct:title", model.getName());
      response.addResultItem(node);
    }
    return response;
  }

  private boolean checkStoragePermission(Dataset dataset, SamAction action) {
    return switch (dataset.storageSystem()) {
      case TERRA_DATA_REPO -> datarepoService.userHasAction(dataset.storageSourceId(), action);
      case TERRA_WORKSPACE, EXTERNAL -> false;
    };
  }

  private void ensureActionPermission(Dataset dataset, SamAction action) {
    // Ensure that the current user has permission to perform this action. The current user
    // can either have permission granted by the storage system that owns the dataset, or if
    // they're a catalog admin user who has permission to perform any operation on any
    // catalog entry.
    if (!checkStoragePermission(dataset, action) && !samService.hasGlobalAction(action)) {
      throw new UnauthorizedException(
          String.format("User does not have permission to %s", action));
    }
  }

  public void deleteMetadata(DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(dataset, SamAction.DELETE_ANY_METADATA);
    datasetDao.delete(dataset);
  }

  public String getMetadata(DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(dataset, SamAction.READ_ANY_METADATA);
    return dataset.metadata();
  }

  public void updateMetadata(DatasetId datasetId, String metadata) {
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(dataset, SamAction.UPDATE_ANY_METADATA);
    datasetDao.update(dataset.withMetadata(metadata));
  }

  public DatasetId createDataset(
      StorageSystem storageSystem, String storageSourceId, String metadata) {
    var dataset = new Dataset(storageSourceId, storageSystem, metadata);
    ensureActionPermission(dataset, SamAction.CREATE_METADATA);
    return datasetDao.create(dataset).id();
  }
}
