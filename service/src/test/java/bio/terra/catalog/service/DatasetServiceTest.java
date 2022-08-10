package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.config.BeanConfig;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.DatasetPreviewTablesResponse;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.rawls.RawlsService;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.model.ColumnModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.SnapshotPreviewModel;
import bio.terra.datarepo.model.TableDataType;
import bio.terra.datarepo.model.TableModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Mock private RawlsService rawlsService;

  @Mock private DatasetDao datasetDao;

  @Mock private SamService samService;

  private final ObjectMapper objectMapper = new BeanConfig().objectMapper();

  private final AuthenticatedUserRequest user = mock(AuthenticatedUserRequest.class);
  private final DatasetId datasetId = new DatasetId(UUID.randomUUID());
  private final String sourceId = "sourceId";

  private final String externalSourceId = "phs000000.v11.p8";
  private final String workspaceSourceId = "abc-def-workspace-id";
  private final String metadata = """
        {"name":"name"}""";
  private final Dataset dataset =
      new Dataset(datasetId, sourceId, StorageSystem.EXTERNAL, metadata, null);
  private final Dataset tdrDataset =
      new Dataset(
          new DatasetId(UUID.randomUUID()),
          sourceId,
          StorageSystem.TERRA_DATA_REPO,
          metadata,
          null);

  private final Dataset externalDataset =
      new Dataset(
          new DatasetId(UUID.randomUUID()),
          externalSourceId,
          StorageSystem.EXTERNAL,
          metadata,
          null);

  private final Dataset workspaceDataset =
      new Dataset(
          new DatasetId(UUID.randomUUID()),
          workspaceSourceId,
          StorageSystem.TERRA_WORKSPACE,
          metadata,
          null);

  @BeforeEach
  public void beforeEach() {
    datasetService =
        new DatasetService(datarepoService, rawlsService, samService, datasetDao, objectMapper);
  }

  private void mockDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
  }

  @Test
  void listDatasets() {
    var workspaces = Map.of(workspaceSourceId, DatasetAccessLevel.OWNER);
    var idToRole = Map.of(sourceId, DatasetAccessLevel.OWNER);
    when(datarepoService.getSnapshotIdsAndRoles(user)).thenReturn(idToRole);
    when(rawlsService.getWorkspaceIdsAndRoles(user)).thenReturn(workspaces);
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, workspaces.keySet()))
        .thenReturn(List.of(workspaceDataset));
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(tdrDataset));
    ObjectNode workspaceJson = (ObjectNode) datasetService.listDatasets(user).getResult().get(0);
    ObjectNode tdrJson = (ObjectNode) datasetService.listDatasets(user).getResult().get(1);
    assertThat(workspaceJson.get("name").asText(), is("name"));
    assertThat(workspaceJson.get("id").asText(), is(workspaceDataset.id().toValue()));
    assertThat(
        workspaceJson.get("accessLevel").asText(), is(String.valueOf(DatasetAccessLevel.OWNER)));
    assertThat(tdrJson.get("name").asText(), is("name"));
    assertThat(tdrJson.get("id").asText(), is(tdrDataset.id().toValue()));
    assertThat(tdrJson.get("accessLevel").asText(), is(String.valueOf(DatasetAccessLevel.OWNER)));
  }

  @Test
  void listDatasetsUsingAdminPermissions() {
    Map<String, DatasetAccessLevel> workspaces = Map.of();
    var datasets = Map.of(sourceId, DatasetAccessLevel.OWNER);
    when(datarepoService.getSnapshotIdsAndRoles(user)).thenReturn(datasets);
    when(rawlsService.getWorkspaceIdsAndRoles(user)).thenReturn(workspaces);
    when(samService.hasGlobalAction(user, SamAction.READ_ANY_METADATA)).thenReturn(true);
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, workspaces.keySet())).thenReturn(List.of());
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, datasets.keySet()))
        .thenReturn(List.of(tdrDataset));
    when(datasetDao.listAllDatasets()).thenReturn(List.of(workspaceDataset, tdrDataset));
    ObjectNode tdrJson = (ObjectNode) datasetService.listDatasets(user).getResult().get(0);
    ObjectNode workspaceJson = (ObjectNode) datasetService.listDatasets(user).getResult().get(1);
    assertThat(tdrJson.get("name").asText(), is("name"));
    assertThat(tdrJson.get("id").asText(), is(tdrDataset.id().toValue()));
    assertThat(tdrJson.get("accessLevel").asText(), is(String.valueOf(DatasetAccessLevel.OWNER)));
    assertThat(workspaceJson.get("name").asText(), is("name"));
    assertThat(workspaceJson.get("id").asText(), is(workspaceDataset.id().toValue()));
    assertThat(
        workspaceJson.get("accessLevel").asText(), is(String.valueOf(DatasetAccessLevel.READER)));
  }

  @Test
  void listDatasetsIllegalMetadata() {
    var badDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, "invalid", null);
    var idToRole = Map.of(sourceId, DatasetAccessLevel.DISCOVERER);
    when(datarepoService.getSnapshotIdsAndRoles(user)).thenReturn(idToRole);
    when(rawlsService.getWorkspaceIdsAndRoles(user)).thenReturn(Map.of());
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, Set.of())).thenReturn(List.of());
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
    reset(datasetDao);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.getRole(user, sourceId)).thenReturn(DatasetAccessLevel.READER);
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
    when(datarepoService.getRole(user, null)).thenReturn(DatasetAccessLevel.DISCOVERER);
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
    when(datarepoService.getPreviewTables(user, tdrDataset.storageSourceId()))
        .thenReturn(
            new SnapshotModel()
                .tables(
                    List.of(
                        new TableModel()
                            .rowCount(1)
                            .columns(
                                List.of(
                                    new ColumnModel()
                                        .datatype(TableDataType.INTEGER)
                                        .name("column a"))))));
    DatasetPreviewTablesResponse results =
        datasetService.listDatasetPreviewTables(user, tdrDataset.id());
    assertThat(results, isA(DatasetPreviewTablesResponse.class));
    assertThat(results.getTables().size(), is(1));
    assertThat(results.getTables().get(0), isA(TableMetadata.class));
    assertThat(results.getTables().get(0).isHasData(), is(true));
  }

  @Test
  void getDatasetPreviewTable() {
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, metadata, null);
    var tableName = "table";
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.getPreviewTables(user, tdrDataset.storageSourceId()))
        .thenReturn(
            new SnapshotModel()
                .tables(
                    List.of(
                        new TableModel()
                            .name(tableName)
                            .rowCount(1)
                            .columns(
                                List.of(
                                    new ColumnModel()
                                        .datatype(TableDataType.INTEGER)
                                        .name("column a"))))));
    when(datarepoService.getPreviewTable(user, tdrDataset.storageSourceId(), tableName))
        .thenReturn(new SnapshotPreviewModel().result(List.of()));
    DatasetPreviewTable datasetPreviewTable =
        datasetService.getDatasetPreview(user, tdrDataset.id(), tableName);
    assertThat(datasetPreviewTable.getRows(), empty());
    assertThat(datasetPreviewTable.getColumns(), hasSize(1));
    assertThat(
        datasetPreviewTable.getColumns().get(0),
        is(new bio.terra.catalog.model.ColumnModel().name("column a").arrayOf(false)));
  }

  @Test
  void testExportSnapshot() {
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.getRole(user, sourceId)).thenReturn(DatasetAccessLevel.READER);
    UUID workspaceId = UUID.randomUUID();
    doThrow(new BadRequestException("error"))
        .when(datarepoService)
        .exportSnapshot(user, sourceId, workspaceId.toString());
    assertThrows(
        BadRequestException.class,
        () -> datasetService.exportDataset(user, datasetId, workspaceId));
  }

  @Test
  void testExportExternalDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(externalDataset);
    UUID workspaceId = UUID.randomUUID();
    assertThrows(
        UnauthorizedException.class,
        () -> datasetService.exportDataset(user, datasetId, workspaceId));
  }

  @Test
  void testExportWorkspaceDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(workspaceDataset);
    when(rawlsService.getRole(user, workspaceSourceId)).thenReturn(DatasetAccessLevel.READER);
    UUID workspaceId = UUID.randomUUID();
    datasetService.exportDataset(user, datasetId, workspaceId);

    DatasetService mockDatasetService = mock(DatasetService.class);
    doNothing().when(mockDatasetService).exportDataset(user, datasetId, workspaceId);
    mockDatasetService.exportDataset(user, datasetId, workspaceId);
    verify(mockDatasetService).exportDataset(user, datasetId, workspaceId);
  }
}
