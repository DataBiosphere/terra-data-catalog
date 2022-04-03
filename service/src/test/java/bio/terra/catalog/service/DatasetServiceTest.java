package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.config.BeanConfig;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

  private DatasetService datasetService;

  @Mock private DatarepoService datarepoService;
  @Mock private DatasetDao datasetDao;
  @Mock private SamService samService;

  private final ObjectMapper objectMapper = new BeanConfig().objectMapper();

  private final DatasetId datasetId = new DatasetId(UUID.randomUUID());
  private final String sourceId = "sourceId";
  private final String metadata = """
        {"name":"name"}""";
  private final Dataset dataset =
      new Dataset(datasetId, sourceId, StorageSystem.EXTERNAL, metadata, null);

  @BeforeEach
  public void beforeEach() {
    datasetService = new DatasetService(datarepoService, samService, datasetDao, objectMapper);
  }

  private void mockDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
  }

  @Test
  void listDatasets() {
    var role = "role";
    var idToRole = Map.of(sourceId, List.of(role));
    when(datarepoService.getSnapshotIdsAndRoles()).thenReturn(idToRole);
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, metadata, null);
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(tdrDataset));
    ObjectNode json = (ObjectNode) datasetService.listDatasets().getResult().get(0);
    assertThat(json.get("name").asText(), is("name"));
    assertThat(json.get("id").asText(), is(tdrDataset.id().toValue()));
    assertThat(json.get("roles").get(0).asText(), is(role));
  }

  @Test
  void listDatasetsIllegalMetadata() {
    var badDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, "invalid", null);
    var idToRole = Map.of(sourceId, List.<String>of());
    when(datarepoService.getSnapshotIdsAndRoles()).thenReturn(idToRole);
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(badDataset));
    assertThrows(
        DatasetService.IllegalMetadataException.class, () -> datasetService.listDatasets());
  }

  @Test()
  void testDeleteMetadataWithInvalidUser() {
    mockDataset();
    assertThrows(UnauthorizedException.class, () -> datasetService.deleteMetadata(datasetId));
  }

  @Test()
  void testDeleteMetadata() {
    mockDataset();
    when(samService.hasGlobalAction(SamAction.DELETE_ANY_METADATA)).thenReturn(true);
    datasetService.deleteMetadata(datasetId);
    verify(datasetDao).delete(dataset);
  }

  @Test
  void testGetMetadataWithInvalidUser() {
    mockDataset();
    assertThrows(UnauthorizedException.class, () -> datasetService.getMetadata(datasetId));
  }

  @Test
  void testGetMetadata() {
    mockDataset();
    when(samService.hasGlobalAction(SamAction.READ_ANY_METADATA)).thenReturn(true);
    datasetService.getMetadata(datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testGetMetadataUsingTdrPermission() {
    var tdrDataset = new Dataset(datasetId, sourceId, StorageSystem.TERRA_DATA_REPO, null, null);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.userHasAction(sourceId, SamAction.READ_ANY_METADATA)).thenReturn(true);
    datasetService.getMetadata(datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testUpdateMetadataWithInvalidUser() {
    mockDataset();
    assertThrows(
        UnauthorizedException.class, () -> datasetService.updateMetadata(datasetId, "test"));
  }

  @Test
  void testUpdateMetadata() {
    mockDataset();
    String metadata = "test metadata";
    when(samService.hasGlobalAction(SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
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

    when(samService.hasGlobalAction(SamAction.CREATE_METADATA)).thenReturn(true);
    when(datasetDao.create(testDataset)).thenReturn(testDatasetWithCreationInfo);
    DatasetId id =
        datasetService.createDataset(StorageSystem.TERRA_DATA_REPO, storageSourceId, metadata);
    assertThat(id, is(datasetId));
  }
}
