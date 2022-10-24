package bio.terra.catalog.service;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.common.StorageSystemService;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.DatasetPreviewTablesResponse;
import bio.terra.catalog.model.DatasetsListResponse;
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
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  public static final String REQUEST_ACCESS_URL_PROPERTY_NAME = "requestAccessURL";
  public static final String PHS_ID_PROPERTY_NAME = "phsId";
  private final DatarepoService datarepoService;
  private final RawlsService rawlsService;
  private final SamService samService;
  private final JsonValidationService jsonValidationService;
  private final DatasetDao datasetDao;
  private final ObjectMapper objectMapper;
  private final StorageSystemService externalService;

  private static final int MAX_ROWS = 30;

  public DatasetService(
      DatarepoService datarepoService,
      RawlsService rawlsService,
      ExternalSystemService externalService,
      SamService samService,
      JsonValidationService jsonValidationService,
      DatasetDao datasetDao,
      ObjectMapper objectMapper) {
    this.datarepoService = datarepoService;
    this.rawlsService = rawlsService;
    this.externalService = externalService;
    this.samService = samService;
    this.jsonValidationService = jsonValidationService;
    this.datasetDao = datasetDao;
    this.objectMapper = objectMapper;
  }

  private StorageSystemService getService(StorageSystem system) {
    return switch (system) {
      case TERRA_WORKSPACE -> rawlsService;
      case TERRA_DATA_REPO -> datarepoService;
      case EXTERNAL -> externalService;
    };
  }

  private StorageSystemService getService(Dataset dataset) {
    return getService(dataset.storageSystem());
  }

  private class DatasetResponse {
    private final Dataset dataset;
    private final StorageSystemInformation storageSystemInformation;

    public DatasetResponse(Dataset dataset) {
      this(
          dataset, new StorageSystemInformation().datasetAccessLevel(DatasetAccessLevel.NO_ACCESS));
    }

    public DatasetResponse(Dataset dataset, StorageSystemInformation storageSystemInformation) {
      this.dataset = dataset;
      this.storageSystemInformation = storageSystemInformation;
    }

    public Object convertToObject() {
      ObjectNode node = toJsonNode(dataset.metadata());
      addPhsProperties(node);
      node.set(
          "accessLevel",
          TextNode.valueOf(String.valueOf(storageSystemInformation.datasetAccessLevel())));
      node.set("id", TextNode.valueOf(dataset.id().toValue()));
      return node;
    }

    private void addPhsProperties(ObjectNode node) {
      if (storageSystemInformation.phsId() != null) {
        node.set(PHS_ID_PROPERTY_NAME, TextNode.valueOf(storageSystemInformation.phsId()));
        if (!node.has(REQUEST_ACCESS_URL_PROPERTY_NAME)) {
          node.set(
              REQUEST_ACCESS_URL_PROPERTY_NAME,
              TextNode.valueOf(
                  String.format(
                      "https://www.ncbi.nlm.nih.gov/projects/gap/cgi-bin/study.cgi?study_id=%s",
                      storageSystemInformation.phsId())));
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

  private List<DatasetResponse> createDatasetResponses(
      Map<String, StorageSystemInformation> datasetStorageInformation,
      StorageSystem storageSystem) {
    List<Dataset> datasets = datasetDao.find(storageSystem, datasetStorageInformation.keySet());
    return datasets.stream()
        .map(
            dataset ->
                new DatasetResponse(
                    dataset, datasetStorageInformation.get(dataset.storageSourceId())))
        .toList();
  }

  private List<DatasetResponse> collectDatasets(
      AuthenticatedUserRequest user, StorageSystem system) {
    // For this storage system, get the collection of visible datasets and the user's roles for
    // each dataset.
    Map<String, StorageSystemInformation> roleMap = getService(system).getDatasets(user);
    return createDatasetResponses(roleMap, system);
  }

  public DatasetsListResponse listDatasets(AuthenticatedUserRequest user) {
    List<DatasetResponse> datasets = new ArrayList<>();
    for (StorageSystem system : StorageSystem.values()) {
      datasets.addAll(collectDatasets(user, system));
    }
    if (samService.hasGlobalAction(user, SamAction.READ_ANY_METADATA)) {
      datasets.addAll(
          datasetDao.listAllDatasets().stream()
              .map(
                  dataset ->
                      new DatasetResponse(
                          dataset,
                          new StorageSystemInformation()
                              .datasetAccessLevel(DatasetAccessLevel.READER)))
              .filter(
                  datasetWithAccessLevel ->
                      !datasets.stream()
                          .map(datasetInList -> datasetInList.dataset.id())
                          .toList()
                          .contains(datasetWithAccessLevel.dataset.id()))
              .toList());
    }
    var response = new DatasetsListResponse();

    response.setResult(datasets.stream().map(DatasetResponse::convertToObject).toList());
    return response;
  }

  private void ensureActionPermission(
      AuthenticatedUserRequest user, Dataset dataset, SamAction action) {
    // Ensure that the current user has permission to perform this action. The current user
    // can either have permission granted by the storage system that owns the dataset, or if
    // they're a catalog admin user who has permission to perform any operation on any
    // catalog entry.
    if (!samService.hasGlobalAction(user, action)
        && !getService(dataset).getRole(user, dataset.storageSourceId()).hasAction(action)) {
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
    return new DatasetResponse(dataset).convertToObject().toString();
  }

  public void updateMetadata(AuthenticatedUserRequest user, DatasetId datasetId, String metadata) {
    jsonValidationService.validateMetadata(toJsonNode(metadata));
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(user, dataset, SamAction.UPDATE_ANY_METADATA);
    datasetDao.update(dataset.withMetadata(metadata));
  }

  public DatasetId createDataset(
      AuthenticatedUserRequest user,
      StorageSystem storageSystem,
      String storageSourceId,
      String metadata) {
    jsonValidationService.validateMetadata(toJsonNode(metadata));
    var dataset = new Dataset(storageSourceId, storageSystem, metadata);
    ensureActionPermission(user, dataset, SamAction.CREATE_METADATA);
    return datasetDao.create(dataset).id();
  }

  public DatasetPreviewTablesResponse listDatasetPreviewTables(
      AuthenticatedUserRequest user, DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    var tableMetadataList = getService(dataset).getPreviewTables(user, dataset.storageSourceId());
    return new DatasetPreviewTablesResponse().tables(tableMetadataList);
  }

  public DatasetPreviewTable getDatasetPreview(
      AuthenticatedUserRequest user, DatasetId datasetId, String tableName) {
    var dataset = datasetDao.retrieve(datasetId);
    return getService(dataset).previewTable(user, dataset.storageSourceId(), tableName, MAX_ROWS);
  }

  public void exportDataset(AuthenticatedUserRequest user, DatasetId datasetId, UUID workspaceId) {
    var dataset = datasetDao.retrieve(datasetId);
    getService(dataset).exportToWorkspace(user, dataset.storageSourceId(), workspaceId.toString());
  }
}
