package bio.terra.catalog.service;

import bio.terra.catalog.common.RequestContextCopier;
import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.common.StorageSystemService;
import bio.terra.catalog.datarepo.DatarepoException;
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
import bio.terra.common.exception.ForbiddenException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  public static final String REQUEST_ACCESS_URL_PROPERTY_NAME = "requestAccessURL";
  public static final String PHS_ID_PROPERTY_NAME = "phsId";

  /**
   * This is used for the admin user to provide default information for datasets that an admin
   * doesn't have access to in the underlying storage system.
   */
  private static final StorageSystemInformation DEFAULT_INFORMATION =
      new StorageSystemInformation(DatasetAccessLevel.READER);

  private final DatarepoService datarepoService;
  private final RawlsService rawlsService;
  private final SamService samService;
  private final JsonValidationService jsonValidationService;
  private final DatasetDao datasetDao;
  private final StorageSystemService externalService;

  private static final int MAX_ROWS = 30;

  public DatasetService(
      DatarepoService datarepoService,
      RawlsService rawlsService,
      ExternalSystemService externalService,
      SamService samService,
      JsonValidationService jsonValidationService,
      DatasetDao datasetDao) {
    this.datarepoService = datarepoService;
    this.rawlsService = rawlsService;
    this.externalService = externalService;
    this.samService = samService;
    this.jsonValidationService = jsonValidationService;
    this.datasetDao = datasetDao;
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

    public DatasetResponse(Dataset dataset, StorageSystemInformation storageSystemInformation) {
      this.dataset = dataset;
      this.storageSystemInformation = storageSystemInformation;
    }

    private Object convertToObject() {
      ObjectNode node = dataset.metadata().deepCopy();
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

  public DatasetsListResponse listDatasets() {
    var systemsAndInfo =
        RequestContextCopier.parallelWithRequest(Arrays.stream(StorageSystem.values()))
            .collect(
                Collectors.toMap(Function.identity(), system -> getService(system).getDatasets()));

    List<Dataset> datasets;
    if (samService.hasGlobalAction(SamAction.READ_ANY_METADATA)) {
      datasets = datasetDao.listAllDatasets();
    } else {
      datasets =
          datasetDao.find(
              systemsAndInfo.entrySet().stream()
                  .collect(
                      Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().keySet())));
    }
    var response = new DatasetsListResponse();
    response.setResult(
        datasets.stream()
            .map(
                dataset ->
                    new DatasetResponse(
                        dataset,
                        systemsAndInfo
                            .getOrDefault(dataset.storageSystem(), Map.of())
                            .getOrDefault(dataset.storageSourceId(), DEFAULT_INFORMATION)))
            .map(DatasetResponse::convertToObject)
            .toList());
    return response;
  }

  private void ensureActionPermission(Dataset dataset, SamAction action) {
    // Ensure that the current user has permission to perform this action. The current user
    // can either have permission granted by the storage system that owns the dataset, or if
    // they're a catalog admin user who has permission to perform any operation on any
    // catalog entry.
    if (!samService.hasGlobalAction(action)
        && !getService(dataset).getRole(dataset.storageSourceId()).hasAction(action)) {
      throw new ForbiddenException(String.format("User does not have permission to %s", action));
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
    StorageSystemInformation information;
    try {
      information = getService(dataset.storageSystem()).getDataset(dataset.storageSourceId());
    } catch (DatarepoException e) {
      information = DEFAULT_INFORMATION;
    }
    return new DatasetResponse(dataset, information).convertToObject().toString();
  }

  public void updateMetadata(DatasetId datasetId, ObjectNode metadata) {
    jsonValidationService.validateMetadata(metadata);
    var dataset = datasetDao.retrieve(datasetId);
    ensureActionPermission(dataset, SamAction.UPDATE_ANY_METADATA);
    datasetDao.update(dataset.withMetadata(metadata));
  }

  public DatasetId upsertDataset(
      StorageSystem storageSystem, String storageSourceId, ObjectNode metadata) {
    jsonValidationService.validateMetadata(metadata);
    var dataset = new Dataset(storageSourceId, storageSystem, metadata);
    ensureActionPermission(dataset, SamAction.CREATE_METADATA);
    return datasetDao.upsert(dataset).id();
  }

  public DatasetPreviewTablesResponse listDatasetPreviewTables(DatasetId datasetId) {
    var dataset = datasetDao.retrieve(datasetId);
    var tableMetadataList = getService(dataset).getPreviewTables(dataset.storageSourceId());
    return new DatasetPreviewTablesResponse().tables(tableMetadataList);
  }

  public DatasetPreviewTable getDatasetPreview(DatasetId datasetId, String tableName) {
    var dataset = datasetDao.retrieve(datasetId);
    return getService(dataset).previewTable(dataset.storageSourceId(), tableName, MAX_ROWS);
  }

  public void exportDataset(DatasetId datasetId, UUID workspaceId) {
    var dataset = datasetDao.retrieve(datasetId);
    getService(dataset).exportToWorkspace(dataset.storageSourceId(), workspaceId.toString());
  }
}
