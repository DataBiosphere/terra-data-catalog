package bio.terra.catalog.api;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.CreatedDatasetId;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.service.DatasetService;
import bio.terra.catalog.service.dataset.DatasetId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class DatasetApiController implements DatasetsApi {
  private final DatasetService datasetService;

  @Autowired
  public DatasetApiController(DatasetService datasetService) {
    this.datasetService = datasetService;
  }

  @Override
  public ResponseEntity<DatasetsListResponse> listDatasets() {
    return ResponseEntity.ok(datasetService.listDatasets());
  }

  @Override
  public ResponseEntity<Void> deleteDataset(UUID id) {
    datasetService.deleteMetadata(new DatasetId(id));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<String> getDataset(UUID id) {
    return ResponseEntity.ok(datasetService.getMetadata(new DatasetId(id)));
  }

  @Override
  public ResponseEntity<Void> updateDataset(UUID id, String metadata) {
    datasetService.updateMetadata(new DatasetId(id), metadata);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CreatedDatasetId> createDataset(CreateDatasetRequest request) {
    var datasetId =
        datasetService.createDataset(
            StorageSystem.fromString(request.getStorageSystem()),
            request.getStorageSourceId(),
            request.getCatalogEntry());
    return ResponseEntity.ok(new CreatedDatasetId().id(datasetId.uuid()));
  }
}
