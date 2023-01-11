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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class DatasetApiController implements DatasetsApi {
  private final DatasetService datasetService;

  @Autowired
  public DatasetApiController(DatasetService datasetService) {
    this.datasetService = datasetService;
  }

  @Autowired ObjectMapper objectMapper;

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
    try {
      datasetService.updateMetadata(
          new DatasetId(id), objectMapper.readValue(metadata, ObjectNode.class));
      return ResponseEntity.noContent().build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ResponseEntity<CreatedDatasetId> upsertDataset(CreateDatasetRequest request) {
    try {
      DatasetId datasetId =
          datasetService.upsertDataset(
              StorageSystem.fromModel(request.getStorageSystem()),
              request.getStorageSourceId(),
              objectMapper.readValue(request.getCatalogEntry().toString(), ObjectNode.class));
      return ResponseEntity.ok(new CreatedDatasetId().id(datasetId.uuid()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
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
