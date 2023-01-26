package bio.terra.catalog.controller;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.CreatedDatasetId;
import bio.terra.catalog.model.DatasetExportRequest;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.DatasetPreviewTablesResponse;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.service.DatasetService;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class DatasetApiController implements DatasetsApi {
  private final DatasetService datasetService;
  private final ObjectMapper objectMapper;

  @Autowired
  public DatasetApiController(DatasetService datasetService, ObjectMapper objectMapper) {
    this.datasetService = datasetService;
    this.objectMapper = objectMapper;
  }

  @VisibleForTesting
  protected ObjectNode toJsonNode(String json) {
    try {
      return objectMapper.readValue(json, ObjectNode.class);
    } catch (JsonProcessingException e) {
      throw new BadRequestException("Catalog metadata must be a valid json object", e);
    }
  }

  @Override
  public ResponseEntity<DatasetsListResponse> listDatasets() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(datasetService.listDatasets());
  }

  @Override
  public ResponseEntity<Void> deleteDataset(UUID id) {
    datasetService.deleteMetadata(new DatasetId(id));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<String> getDataset(UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(datasetService.getMetadata(new DatasetId(id)));
  }

  @Override
  public ResponseEntity<Void> updateDataset(UUID id, String metadata) {
    datasetService.updateMetadata(new DatasetId(id), toJsonNode(metadata));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CreatedDatasetId> upsertDataset(CreateDatasetRequest request) {
    DatasetId datasetId =
        datasetService.upsertDataset(
            StorageSystem.fromModel(request.getStorageSystem()),
            request.getStorageSourceId(),
            objectMapper.convertValue(request.getCatalogEntry(), ObjectNode.class));
    return ResponseEntity.ok(new CreatedDatasetId().id(datasetId.uuid()));
  }

  @Override
  public ResponseEntity<DatasetPreviewTablesResponse> listDatasetPreviewTables(UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(datasetService.listDatasetPreviewTables(new DatasetId(id)));
  }

  @Override
  public ResponseEntity<DatasetPreviewTable> getDatasetPreviewTable(UUID id, String tableName) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(datasetService.getDatasetPreview(new DatasetId(id), tableName));
  }

  @Override
  public ResponseEntity<Void> exportDataset(UUID datasetId, DatasetExportRequest body) {
    datasetService.exportDataset(new DatasetId(datasetId), body.getWorkspaceId());
    return ResponseEntity.noContent().build();
  }
}
