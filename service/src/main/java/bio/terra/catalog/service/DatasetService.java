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
import bio.terra.common.iam.AuthenticatedUserRequest;
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

  public DatasetsListResponse listDatasets(AuthenticatedUserRequest user) {
    var response = new DatasetsListResponse();
    for (SnapshotSummaryModel model : datarepoService.getSnapshots(user)) {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("id", model.getId().toString());
      node.put("dct:title", model.getName());
      response.addResultItem(node);
    }
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
    if (!checkStoragePermission(user, dataset, action) && !samService.hasAction(user, action)) {
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
    ensureActionPermission(user, dataset, SamAction.UPDATE_ANY_METADATA);
    return datasetDao.create(dataset).id();
  }
}
