package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.config.BeanConfig;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatasetServiceTest {

  private DatasetService datasetService;

  private final DatarepoService datarepoService = mock(DatarepoService.class);
  private final DatasetDao datasetDao = mock(DatasetDao.class);
  private final ObjectMapper objectMapper = new BeanConfig().objectMapper();
  private final SamService samService = mock(SamService.class);
  private final SamAuthenticatedUserRequestFactory userFactory =
      mock(SamAuthenticatedUserRequestFactory.class);

  private final DatasetId datasetId = new DatasetId(UUID.randomUUID());
  private final String sourceId = "sourceId";
  private final Dataset dataset =
      new Dataset(datasetId, sourceId, StorageSystem.EXTERNAL, null, null);

  @BeforeEach
  public void beforeEach() {
    datasetService =
        new DatasetService(datarepoService, samService, datasetDao, objectMapper, userFactory);
    when(userFactory.getUser()).thenReturn(mock(AuthenticatedUserRequest.class));
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
  }

  @Test
  void listDatasets() {
    var model = new SnapshotSummaryModel().id(UUID.randomUUID()).name("test");
    when(datarepoService.getSnapshots()).thenReturn(List.of(model));
    var result = datasetService.listDatasets();
    assertThat(result, is(notNullValue()));
  }

  @Test()
  void testDeleteMetadataWithInvalidUser() {
    assertThrows(UnauthorizedException.class, () -> datasetService.deleteMetadata(datasetId));
  }

  @Test()
  void testDeleteMetadata() {
    when(samService.hasAction(SamAction.DELETE_ANY_METADATA)).thenReturn(true);
    datasetService.deleteMetadata(datasetId);
    verify(datasetDao).delete(dataset);
  }

  @Test
  void testGetMetadataWithInvalidUser() {
    assertThrows(UnauthorizedException.class, () -> datasetService.getMetadata(datasetId));
  }

  @Test
  void testGetMetadata() {
    when(samService.hasAction(SamAction.READ_ANY_METADATA)).thenReturn(true);
    datasetService.getMetadata(datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testGetMetadataUsingTdrPermission() {
    var tdrDataset = new Dataset(datasetId, sourceId, StorageSystem.TERRA_DATA_REPO, null, null);
    reset(datasetDao);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.userHasAction(sourceId, SamAction.READ_ANY_METADATA)).thenReturn(true);
    datasetService.getMetadata(datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testUpdateMetadataWithInvalidUser() {
    assertThrows(
        UnauthorizedException.class, () -> datasetService.updateMetadata(datasetId, "test"));
  }

  @Test
  void testUpdateMetadata() {
    String metadata = "test metadata";
    when(samService.hasAction(SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    datasetService.updateMetadata(datasetId, metadata);
    verify(datasetDao).update(dataset.withMetadata(metadata));
  }

  @Test
  void testCreateDatasetWithInvalidUser() {
    assertThrows(
        UnauthorizedException.class,
        () -> datasetService.createDataset(StorageSystem.TERRA_DATA_REPO, null, null));
  }

  @Test
  void testCreateDataset() {
    String metadata = "test metadata";
    String storageSourceId = "testSource";
    Dataset testDataset = new Dataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, metadata);
    Dataset testDatasetWithCreationInfo =
        new Dataset(
            datasetId, storageSourceId, StorageSystem.TERRA_DATA_REPO, metadata, Instant.now());

    when(samService.hasAction(SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    when(datasetDao.create(testDataset)).thenReturn(testDatasetWithCreationInfo);
    DatasetId id =
        datasetService.createDataset(StorageSystem.TERRA_DATA_REPO, storageSourceId, metadata);
    assertThat(id, is(datasetId));
  }
}
