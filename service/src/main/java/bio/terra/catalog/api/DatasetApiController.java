package bio.terra.catalog.api;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.CreatedDatasetId;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.service.DatasetService;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.common.iam.AuthenticatedUserRequestFactory;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class DatasetApiController implements DatasetsApi {
  private final HttpServletRequest request;
  private final DatasetService datasetService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;

  @Autowired
  public DatasetApiController(
      HttpServletRequest request,
      DatasetService datasetService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory) {
    this.request = request;
    this.datasetService = datasetService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
  }

  private AuthenticatedUserRequest getUser() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<DatasetsListResponse> listDatasets() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(datasetService.listDatasets(getUser()));
    // return ResponseEntity.ok(datasetService.listDatasets(getUser()));
  }

  @Override
  public ResponseEntity<Void> deleteDataset(UUID id) {
    datasetService.deleteMetadata(getUser(), new DatasetId(id));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<String> getDataset(UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(datasetService.getMetadata(getUser(), new DatasetId(id)));
  }

  @Override
  public ResponseEntity<Void> updateDataset(UUID id, String metadata) {
    datasetService.updateMetadata(getUser(), new DatasetId(id), metadata);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CreatedDatasetId> createDataset(CreateDatasetRequest request) {
    AuthenticatedUserRequest user = getUser();
    var datasetId =
        datasetService.createDataset(
            user,
            StorageSystem.fromModel(request.getStorageSystem()),
            request.getStorageSourceId(),
            request.getCatalogEntry());
    return ResponseEntity.ok(new CreatedDatasetId().id(datasetId.uuid()));
  }
}
