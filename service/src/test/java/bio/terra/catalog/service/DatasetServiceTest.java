package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.config.BeanConfig;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.ColumnModel;
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

  @Mock private RawlsService rawlsService;

  @Mock private DatasetDao datasetDao;

  @Mock private SamService samService;

  private static final ObjectMapper objectMapper = new BeanConfig().objectMapper();

  private static final AuthenticatedUserRequest user = mock(AuthenticatedUserRequest.class);
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
            datarepoService,
            rawlsService,
            new ExternalSystemService(),
            samService,
            datasetDao,
            objectMapper);
  }

  private void mockDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
  }

  @Test
  void listDatasets() {
    var workspaces = Map.of(workspaceSourceId, DatasetAccessLevel.OWNER);
    var idToRole = Map.of(sourceId, DatasetAccessLevel.OWNER);
    when(datarepoService.getIdsAndRoles(user)).thenReturn(idToRole);
    when(rawlsService.getIdsAndRoles(user)).thenReturn(workspaces);
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
    when(datarepoService.getIdsAndRoles(user)).thenReturn(datasets);
    when(rawlsService.getIdsAndRoles(user)).thenReturn(workspaces);
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
    // External datasets can be read by anyone.
    mockDataset();
    assertThat(datasetService.getMetadata(user, datasetId), is(metadata));

    when(datasetDao.retrieve(tdrDataset.id())).thenReturn(tdrDataset);
    when(datarepoService.getRole(user, tdrDataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
    assertThrows(
        UnauthorizedException.class, () -> datasetService.getMetadata(user, tdrDataset.id()));

    when(datasetDao.retrieve(workspaceDataset.id())).thenReturn(workspaceDataset);
    when(rawlsService.getRole(user, workspaceDataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
    assertThrows(
        UnauthorizedException.class, () -> datasetService.getMetadata(user, workspaceDataset.id()));
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
        UnauthorizedException.class,
        () -> datasetService.updateMetadata(user, datasetId, metadata));
  }

  @Test
  void testUpdateMetadata() {
    mockDataset();
    when(samService.hasGlobalAction(user, SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    datasetService.updateMetadata(user, datasetId, metadata);
    verify(datasetDao).update(dataset.withMetadata(metadata));
  }

  @Test
  void testUpdateMetadataInvalidInput() {
    String invalidMetadata = "metadata must be json object";
    assertThrows(
        BadRequestException.class,
        () -> datasetService.updateMetadata(user, datasetId, invalidMetadata));
    verify(datasetDao, never()).update(any());
  }

  @Test
  void testCreateDatasetWithInvalidUser() {
    when(datarepoService.getRole(user, null)).thenReturn(DatasetAccessLevel.DISCOVERER);
    assertThrows(
        UnauthorizedException.class,
        () -> datasetService.createDataset(user, StorageSystem.TERRA_DATA_REPO, null, metadata));
  }

  @Test
  void testCreateDataset() {
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
  void testCreateDatasetInvalidMetadata() {
    String invalidMetadata = "metadata must be json object";
    String storageSourceId = "testSource";
    assertThrows(
        BadRequestException.class,
        () ->
            datasetService.createDataset(
                user, StorageSystem.TERRA_DATA_REPO, storageSourceId, invalidMetadata));
    verify(datasetDao, never()).create(any());
  }

  @Test
  void listDatasetPreviewTables() {
    var tables =
        List.of(
            new TableMetadata().name("table").hasData(true),
            new TableMetadata().name("empty").hasData(false));
    var response = new DatasetPreviewTablesResponse().tables(tables);
    when(datasetDao.retrieve(tdrDataset.id())).thenReturn(tdrDataset);
    when(datarepoService.getPreviewTables(user, tdrDataset.storageSourceId())).thenReturn(tables);
    assertThat(datasetService.listDatasetPreviewTables(user, tdrDataset.id()), is(response));

    when(datasetDao.retrieve(workspaceDataset.id())).thenReturn(workspaceDataset);
    when(rawlsService.getPreviewTables(user, workspaceDataset.storageSourceId()))
        .thenReturn(tables);
    assertThat(datasetService.listDatasetPreviewTables(user, workspaceDataset.id()), is(response));

    mockDataset();
    assertThat(
        datasetService.listDatasetPreviewTables(user, datasetId),
        is(new DatasetPreviewTablesResponse().tables(List.of())));
  }

  @Test
  void getDatasetPreviewTable() {
    var tableName = "table";
    when(datasetDao.retrieve(tdrDataset.id())).thenReturn(tdrDataset);
    var previewTable = new DatasetPreviewTable().columns(List.of(new ColumnModel().name("test")));
    when(datarepoService.previewTable(user, tdrDataset.storageSourceId(), tableName, 30))
        .thenReturn(previewTable);
    assertThat(
        datasetService.getDatasetPreview(user, tdrDataset.id(), tableName), is(previewTable));

    when(datasetDao.retrieve(workspaceDataset.id())).thenReturn(workspaceDataset);
    when(rawlsService.previewTable(user, workspaceDataset.storageSourceId(), tableName, 30))
        .thenReturn(previewTable);
    assertThat(
        datasetService.getDatasetPreview(user, workspaceDataset.id(), tableName), is(previewTable));

    mockDataset();
    assertThat(
        datasetService.getDatasetPreview(user, datasetId, tableName),
        is(new DatasetPreviewTable()));
  }

  @Test
  void exportDataset() {
    UUID workspaceId = UUID.randomUUID();

    when(datasetDao.retrieve(tdrDataset.id())).thenReturn(tdrDataset);
    datasetService.exportDataset(user, tdrDataset.id(), workspaceId);
    verify(datarepoService)
        .exportToWorkspace(user, tdrDataset.storageSourceId(), workspaceId.toString());

    when(datasetDao.retrieve(workspaceDataset.id())).thenReturn(workspaceDataset);
    datasetService.exportDataset(user, workspaceDataset.id(), workspaceId);
    verify(rawlsService)
        .exportToWorkspace(user, workspaceDataset.storageSourceId(), workspaceId.toString());

    mockDataset();
    datasetService.exportDataset(user, datasetId, workspaceId);
    // To verify calls to the external service, it must be mocked.
  }
}
