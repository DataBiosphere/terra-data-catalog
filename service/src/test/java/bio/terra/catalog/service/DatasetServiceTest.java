package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import bio.terra.datarepo.model.ColumnModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.SnapshotPreviewModel;
import bio.terra.datarepo.model.TableDataType;
import bio.terra.datarepo.model.TableModel;
import bio.terra.rawls.model.Entity;
import bio.terra.rawls.model.EntityQueryResponse;
import bio.terra.rawls.model.EntityTypeMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.HashMap;
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

  @Mock private RawlsService rawlsService;

  @Mock private DatasetDao datasetDao;

  @Mock private SamService samService;

  private static final ObjectMapper objectMapper = new BeanConfig().objectMapper();

  private static final DatasetId datasetId = new DatasetId(UUID.randomUUID());
  private static final String sourceId = "sourceId";
  private static final String workspaceSourceId = "abc-def-workspace-id";
  private static final String metadata = """
        {"name":"name"}""";
  private static final Dataset dataset =
      new Dataset(datasetId, sourceId, StorageSystem.EXTERNAL, metadata, null);
  private static final Dataset tdrDataset =
      new Dataset(
          new DatasetId(UUID.randomUUID()),
          sourceId,
          StorageSystem.TERRA_DATA_REPO,
          metadata,
          null);

  private static final Dataset workspaceDataset =
      new Dataset(
          new DatasetId(UUID.randomUUID()),
          workspaceSourceId,
          StorageSystem.TERRA_WORKSPACE,
          metadata,
          null);

  @BeforeEach
  public void beforeEach() {
    datasetService =
        new DatasetService(
            datarepoService, rawlsService, samService, datasetDao, objectMapper);
  }

  private void mockDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
  }

  @Test
  void listDatasets() {
    var workspaces = Map.of(workspaceSourceId, DatasetAccessLevel.OWNER);
    var idToRole = Map.of(sourceId, DatasetAccessLevel.OWNER);
    when(datarepoService.getSnapshotIdsAndRoles()).thenReturn(idToRole);
    when(rawlsService.getWorkspaceIdsAndRoles()).thenReturn(workspaces);
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, workspaces.keySet()))
        .thenReturn(List.of(workspaceDataset));
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(tdrDataset));
    ObjectNode workspaceJson = (ObjectNode) datasetService.listDatasets().getResult().get(0);
    ObjectNode tdrJson = (ObjectNode) datasetService.listDatasets().getResult().get(1);
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
    when(datarepoService.getSnapshotIdsAndRoles()).thenReturn(datasets);
    when(rawlsService.getWorkspaceIdsAndRoles()).thenReturn(workspaces);
    when(samService.hasGlobalAction(SamAction.READ_ANY_METADATA)).thenReturn(true);
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, workspaces.keySet())).thenReturn(List.of());
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, datasets.keySet()))
        .thenReturn(List.of(tdrDataset));
    when(datasetDao.listAllDatasets()).thenReturn(List.of(workspaceDataset, tdrDataset));
    ObjectNode tdrJson = (ObjectNode) datasetService.listDatasets().getResult().get(0);
    ObjectNode workspaceJson = (ObjectNode) datasetService.listDatasets().getResult().get(1);
    assertThat(tdrJson.get("name").asText(), is("name"));
    assertThat(tdrJson.get("id").asText(), is(tdrDataset.id().toValue()));
    assertThat(tdrJson.get("accessLevel").asText(), is(String.valueOf(DatasetAccessLevel.OWNER)));
    assertThat(workspaceJson.get("name").asText(), is("name"));
    assertThat(workspaceJson.get("id").asText(), is(workspaceDataset.id().toValue()));
    assertThat(
        workspaceJson.get("accessLevel").asText(), is(String.valueOf(DatasetAccessLevel.READER)));
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
    reset(datasetDao);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.getRole(sourceId)).thenReturn(DatasetAccessLevel.READER);
    datasetService.getMetadata(datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testUpdateMetadataWithInvalidUser() {
    mockDataset();
    assertThrows(
        UnauthorizedException.class, () -> datasetService.updateMetadata(datasetId, metadata));
  }

  @Test
  void testUpdateMetadata() {
    mockDataset();
    when(samService.hasGlobalAction(SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    datasetService.updateMetadata(datasetId, metadata);
    verify(datasetDao).update(dataset.withMetadata(metadata));
  }

  @Test
  void testUpdateMetadataInvalidInput() {
    String invalidMetadata = "metadata must be json object";
    assertThrows(
        BadRequestException.class, () -> datasetService.updateMetadata(datasetId, invalidMetadata));
    verify(datasetDao, never()).update(any());
  }

  @Test
  void testCreateDatasetWithInvalidUser() {
    when(datarepoService.getRole(null)).thenReturn(DatasetAccessLevel.DISCOVERER);
    assertThrows(
        UnauthorizedException.class,
        () -> datasetService.createDataset(StorageSystem.TERRA_DATA_REPO, null, metadata));
  }

  @Test
  void testCreateDataset() {
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

  @Test
  void testCreateDatasetInvalidMetadata() {
    String invalidMetadata = "metadata must be json object";
    String storageSourceId = "testSource";
    assertThrows(
        BadRequestException.class,
        () ->
            datasetService.createDataset(
                StorageSystem.TERRA_DATA_REPO, storageSourceId, invalidMetadata));
    verify(datasetDao, never()).create(any());
  }

  @Test
  void getDatasetPreviewTablesRepo() {
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, metadata, null);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.getPreviewTables(tdrDataset.storageSourceId()))
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
    DatasetPreviewTablesResponse results = datasetService.listDatasetPreviewTables(tdrDataset.id());
    assertThat(results, isA(DatasetPreviewTablesResponse.class));
    assertThat(results.getTables().size(), is(1));
    assertThat(results.getTables().get(0), isA(TableMetadata.class));
    assertThat(results.getTables().get(0).isHasData(), is(true));
  }

  @Test
  void getDatasetPreviewTablesWorkspace() {
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_WORKSPACE, metadata, null);
    Map<String, EntityTypeMetadata> map = new HashMap<>();
    map.put("str", new EntityTypeMetadata().count(3));
    when(datasetDao.retrieve(tdrDataset.id())).thenReturn(tdrDataset);
    when(rawlsService.entityMetadata(tdrDataset.storageSourceId())).thenReturn(map);
    DatasetPreviewTablesResponse results = datasetService.listDatasetPreviewTables(tdrDataset.id());
    assertThat(results.getTables().size(), is(1));
    assertThat(results.getTables().get(0), isA(TableMetadata.class));
    assertThat(results.getTables().get(0).isHasData(), is(true));
  }

  @Test
  void getDatasetPreviewTableRepo() {
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, metadata, null);
    var tableName = "table";
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.getPreviewTables(tdrDataset.storageSourceId()))
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
    when(datarepoService.getPreviewTable(tdrDataset.storageSourceId(), tableName))
        .thenReturn(new SnapshotPreviewModel().result(List.of()));
    DatasetPreviewTable datasetPreviewTable =
        datasetService.getDatasetPreview(tdrDataset.id(), tableName);
    assertThat(datasetPreviewTable.getRows(), empty());
    assertThat(datasetPreviewTable.getColumns(), hasSize(1));
    assertThat(
        datasetPreviewTable.getColumns().get(0),
        is(new bio.terra.catalog.model.ColumnModel().name("column a").arrayOf(false)));
  }

  @Test
  void getDatasetPreviewTableWorkspace() {
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_WORKSPACE, metadata, null);
    var tableName = "sample";
    List<String> att = List.of("a", "b", "c");
    EntityTypeMetadata ent = new EntityTypeMetadata().idName("idName").attributeNames(att);
    Map<String, EntityTypeMetadata> map = new HashMap<>();
    map.put("str", new EntityTypeMetadata());
    map.put(tableName, ent);
    Entity entity = new Entity().name("sample");
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(rawlsService.entityMetadata(tdrDataset.storageSourceId())).thenReturn(map);
    when(rawlsService.entityQuery(tdrDataset.storageSourceId(), tableName))
        .thenReturn(new EntityQueryResponse().results(List.of(entity)));
    DatasetPreviewTable datasetPreviewTable =
        datasetService.getDatasetPreview(tdrDataset.id(), tableName);
    assertThat(datasetPreviewTable.getRows(), hasSize(1));
    assertThat(datasetPreviewTable.getColumns(), hasSize(4));
    assertThat(
        datasetPreviewTable.getColumns().get(0),
        is(new bio.terra.catalog.model.ColumnModel().name("idName")));
  }

  @Test
  void testExportSnapshot() {
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    UUID workspaceId = UUID.randomUUID();
    doThrow(new BadRequestException("error"))
        .when(datarepoService)
        .exportSnapshot(sourceId, workspaceId.toString());
    assertThrows(
        BadRequestException.class, () -> datasetService.exportDataset(datasetId, workspaceId));
  }

  @Test
  void testExportWorkspaceDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(workspaceDataset);
    UUID workspaceId = UUID.randomUUID();
    datasetService.exportDataset(datasetId, workspaceId);
    verify(rawlsService)
        .exportWorkspaceDataset(workspaceDataset.storageSourceId(), workspaceId.toString());
  }
}
