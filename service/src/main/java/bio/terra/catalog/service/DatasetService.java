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
import bio.terra.common.iam.AuthenticatedUserRequest;
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

  private void collectDatarepoDatasets(DatasetsListResponse response) {
    // For this storage system, get the collection of visible datasets and the user's roles for
    // each dataset.
    var roleMap = datarepoService.getSnapshotIdsAndRoles();

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

  public DatasetsListResponse listDatasets() {
    var response = new DatasetsListResponse();
    collectDatarepoDatasets(response);
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
      throw new UnauthorizedException(String.format("User does not have permission to %s", action));
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
