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
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.DatasetPreviewTablesResponse;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.model.ColumnModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.TableDataType;
import bio.terra.datarepo.model.TableModel;
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

  private final AuthenticatedUserRequest user = mock(AuthenticatedUserRequest.class);
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
    when(datarepoService.getSnapshotIdsAndRoles(user)).thenReturn(idToRole);
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, metadata, null);
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(tdrDataset));
    ObjectNode json = (ObjectNode) datasetService.listDatasets(user).getResult().get(0);
    assertThat(json.get("name").asText(), is("name"));
    assertThat(json.get("id").asText(), is(tdrDataset.id().toValue()));
    assertThat(json.get("roles").get(0).asText(), is(role));
  }

  @Test
  void listDatasetsIllegalMetadata() {
    var badDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, "invalid", null);
    var idToRole = Map.of(sourceId, List.<String>of());
    when(datarepoService.getSnapshotIdsAndRoles(user)).thenReturn(idToRole);
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(badDataset));
    assertThrows(
        DatasetService.IllegalMetadataException.class, () -> datasetService.listDatasets(user));
  }

  @Test()
  void testDeleteMetadataWithInvalidUser() {
    mockDataset();
    assertThrows(UnauthorizedException.class, () -> datasetService.deleteMetadata(user, datasetId));
  }

  @Test()
  void testDeleteMetadata() {
    mockDataset();
    when(samService.hasGlobalAction(user, SamAction.DELETE_ANY_METADATA)).thenReturn(true);
    datasetService.deleteMetadata(user, datasetId);
    verify(datasetDao).delete(dataset);
  }

  @Test
  void testGetMetadataWithInvalidUser() {
    mockDataset();
    assertThrows(UnauthorizedException.class, () -> datasetService.getMetadata(user, datasetId));
  }

  @Test
  void testGetMetadata() {
    mockDataset();
    when(samService.hasGlobalAction(user, SamAction.READ_ANY_METADATA)).thenReturn(true);
    datasetService.getMetadata(user, datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testGetMetadataUsingTdrPermission() {
    var tdrDataset = new Dataset(datasetId, sourceId, StorageSystem.TERRA_DATA_REPO, null, null);
    reset(datasetDao);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.userHasAction(user, sourceId, SamAction.READ_ANY_METADATA))
        .thenReturn(true);
    datasetService.getMetadata(user, datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testUpdateMetadataWithInvalidUser() {
    mockDataset();
    assertThrows(
        UnauthorizedException.class, () -> datasetService.updateMetadata(user, datasetId, "test"));
  }

  @Test
  void testUpdateMetadata() {
    mockDataset();
    String metadata = "test metadata";
    when(samService.hasGlobalAction(user, SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    datasetService.updateMetadata(user, datasetId, metadata);
    verify(datasetDao).update(dataset.withMetadata(metadata));
  }

  @Test
  void testCreateDatasetWithInvalidUser() {
    assertThrows(
        UnauthorizedException.class,
        () -> datasetService.createDataset(user, StorageSystem.TERRA_DATA_REPO, null, null));
  }

  @Test
  void testCreateDataset() {
    String metadata = "test metadata";
    String storageSourceId = "testSource";
    Dataset testDataset = new Dataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, metadata);
    Dataset testDatasetWithCreationInfo =
        new Dataset(
            datasetId, storageSourceId, StorageSystem.TERRA_DATA_REPO, metadata, Instant.now());

    when(samService.hasGlobalAction(user, SamAction.CREATE_METADATA)).thenReturn(true);
    when(datasetDao.create(testDataset)).thenReturn(testDatasetWithCreationInfo);
    DatasetId id =
        datasetService.createDataset(
            user, StorageSystem.TERRA_DATA_REPO, storageSourceId, metadata);
    assertThat(id, is(datasetId));
  }

  @Test
  void getDatasetPreviewTables() {
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, metadata, null);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(samService.hasGlobalAction(user, SamAction.READ_ANY_METADATA)).thenReturn(true);
    when(datarepoService.getPreviewTables(user, tdrDataset.storageSourceId()))
        .thenReturn(
            new SnapshotModel()
                .tables(
                    List.of(
                        new TableModel()
                            .columns(
                                List.of(
                                    new ColumnModel()
                                        .name("column a"))))));
    DatasetPreviewTablesResponse results =
        datasetService.getDatasetPreviewTables(user, tdrDataset.id());
    // Test that all the data conversion works as expected
    assertThat(results, isA(DatasetPreviewTablesResponse.class));
    assertThat(results.getTables().size(), is(1));
    assertThat(results.getTables().get(0), isA(bio.terra.catalog.model.TableModel.class));
    assertThat(
        results.getTables().get(0).getColumns().get(0),
        isA(bio.terra.catalog.model.ColumnModel.class));
  }
}
