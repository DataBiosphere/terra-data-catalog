package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.common.StorageSystemInformation;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {
  private DatasetService datasetService;

  @Mock private JsonValidationService jsonValidationService;

  @Mock private DatarepoService datarepoService;

  @Mock private RawlsService rawlsService;

  @Mock private ExternalSystemService externalSystemService;

  @Mock private DatasetDao datasetDao;

  @Mock private SamService samService;

  private static final ObjectMapper objectMapper = new BeanConfig().objectMapper();

  private static final DatasetId datasetId = new DatasetId(UUID.randomUUID());
  private static final String SOURCE_ID = "sourceId";
  private static final String WORKSPACE_ID = "abc-def-workspace-id";
  private static final String METADATA = """
      {"name":"name"}""";
  private static final Dataset dataset =
      new Dataset(datasetId, SOURCE_ID, StorageSystem.EXTERNAL, METADATA, null);
  private static final Dataset tdrDataset =
      new Dataset(
          new DatasetId(UUID.randomUUID()),
          SOURCE_ID,
          StorageSystem.TERRA_DATA_REPO,
          METADATA,
          null);

  private static final Dataset workspaceDataset =
      new Dataset(
          new DatasetId(UUID.randomUUID()),
          WORKSPACE_ID,
          StorageSystem.TERRA_WORKSPACE,
          METADATA,
          null);

  private static String metadataWithId(DatasetId id) {
    return """
        {"name":"name","accessLevel":"no_access","id":"%s"}""".formatted(id.uuid());
  }

  @BeforeEach
  public void beforeEach() {
    datasetService =
        new DatasetService(
            datarepoService,
            rawlsService,
            externalSystemService,
            samService,
            jsonValidationService,
            datasetDao,
            objectMapper);
  }

  private void mockDataset() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
  }

  @Test
  void listDatasets() {
    var workspaces =
        Map.of(
            WORKSPACE_ID,
            new StorageSystemInformation().datasetAccessLevel(DatasetAccessLevel.OWNER));
    var idToRole =
        Map.of(
            SOURCE_ID, new StorageSystemInformation().datasetAccessLevel(DatasetAccessLevel.OWNER));
    when(datarepoService.getDatasets()).thenReturn(idToRole);
    when(rawlsService.getDatasets()).thenReturn(workspaces);
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
  void listDatasetsWithPhsId() {
    String phsId = "1234";
    var idToRole =
        Map.of(
            SOURCE_ID,
            new StorageSystemInformation()
                .datasetAccessLevel(DatasetAccessLevel.OWNER)
                .phsId(phsId));
    when(datarepoService.getDatasets()).thenReturn(idToRole);
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, Set.of())).thenReturn(List.of());
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(tdrDataset));
    ObjectNode tdrJson = (ObjectNode) datasetService.listDatasets().getResult().get(0);
    assertThat(tdrJson.get("phsId").asText(), is(phsId));
    assertTrue(tdrJson.has("requestAccessURL"));
  }

  @Test
  void listDatasetsWithPhsIdOverride() {
    String phsId = "1234";
    var idToRole =
        Map.of(
            SOURCE_ID,
            new StorageSystemInformation()
                .datasetAccessLevel(DatasetAccessLevel.OWNER)
                .phsId(phsId));
    when(datarepoService.getDatasets()).thenReturn(idToRole);
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, Set.of())).thenReturn(List.of());
    var url = "url";
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(
            List.of(
                tdrDataset.withMetadata(
                    """
            {"%s":"%s"}"""
                        .formatted(DatasetService.REQUEST_ACCESS_URL_PROPERTY_NAME, url))));
    ObjectNode tdrJson = (ObjectNode) datasetService.listDatasets().getResult().get(0);
    assertThat(tdrJson.get("phsId").asText(), is(phsId));
    assertThat(tdrJson.get(DatasetService.REQUEST_ACCESS_URL_PROPERTY_NAME).asText(), is(url));
  }

  @Test
  void listDatasetsUsingAdminPermissions() {
    Map<String, StorageSystemInformation> workspaces = Map.of();
    var datasets =
        Map.of(
            SOURCE_ID, new StorageSystemInformation().datasetAccessLevel(DatasetAccessLevel.OWNER));
    when(datarepoService.getDatasets()).thenReturn(datasets);
    when(rawlsService.getDatasets()).thenReturn(workspaces);
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
    when(externalSystemService.getRole(dataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
    assertThrows(UnauthorizedException.class, () -> datasetService.deleteMetadata(datasetId));
  }

  @Test()
  void testDeleteMetadata() {
    mockDataset();
    when(externalSystemService.getRole(SOURCE_ID)).thenReturn(DatasetAccessLevel.OWNER);
    datasetService.deleteMetadata(datasetId);
    verify(datasetDao).delete(dataset);
  }

  @Test()
  void testDeleteMetadataNoPermission() {
    mockDataset();
    when(externalSystemService.getRole(SOURCE_ID)).thenReturn(DatasetAccessLevel.READER);
    assertThrows(UnauthorizedException.class, () -> datasetService.deleteMetadata(datasetId));
  }

  @Test
  void testGetMetadataWithInvalidUser() {
    mockDataset();
    when(externalSystemService.getRole(dataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
    assertThrows(UnauthorizedException.class, () -> datasetService.getMetadata(datasetId));
  }

  @Test
  void testGetMetadata() throws Exception {
    mockDataset();
    when(samService.hasGlobalAction(SamAction.READ_ANY_METADATA)).thenReturn(true);
    JSONAssert.assertEquals(metadataWithId(datasetId), datasetService.getMetadata(datasetId), true);
  }

  @Test
  void testUpdateMetadataWithInvalidUser() {
    mockDataset();
    when(externalSystemService.getRole(dataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
    assertThrows(
        UnauthorizedException.class, () -> datasetService.updateMetadata(datasetId, METADATA));
  }

  @Test
  void testUpdateMetadata() throws JsonProcessingException {
    mockDataset();
    when(samService.hasGlobalAction(SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    datasetService.updateMetadata(datasetId, METADATA);
    verify(jsonValidationService).validateMetadata(objectMapper.readTree(dataset.metadata()));
    verify(datasetDao).update(dataset.withMetadata(METADATA));
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
        () -> datasetService.upsertDataset(StorageSystem.TERRA_DATA_REPO, null, METADATA));
  }

  @Test
  void testCreateDatasetAdmin() throws JsonProcessingException {
    when(samService.hasGlobalAction(SamAction.CREATE_METADATA)).thenReturn(true);
    when(datasetDao.upsert(new Dataset(SOURCE_ID, dataset.storageSystem(), METADATA)))
        .thenReturn(dataset);
    DatasetId id = datasetService.upsertDataset(dataset.storageSystem(), SOURCE_ID, METADATA);
    verify(jsonValidationService).validateMetadata(objectMapper.readTree(dataset.metadata()));
    assertThat(id, is(datasetId));
  }

  @Test
  void testCreateDatasetInvalidMetadata() {
    String invalidMetadata = "metadata must be json object";
    String storageSourceId = "testSource";
    assertThrows(
        BadRequestException.class,
        () ->
            datasetService.upsertDataset(
                StorageSystem.TERRA_DATA_REPO, storageSourceId, invalidMetadata));
    verify(datasetDao, never()).upsert(any());
  }

  @Test
  void listDatasetPreviewTables() {
    var tables =
        List.of(
            new TableMetadata().name("table").hasData(true),
            new TableMetadata().name("empty").hasData(false));
    var response = new DatasetPreviewTablesResponse().tables(tables);
    mockDataset();
    when(externalSystemService.getPreviewTables(tdrDataset.storageSourceId())).thenReturn(tables);
    assertThat(datasetService.listDatasetPreviewTables(datasetId), is(response));
  }

  @Test
  void getDatasetPreviewTable() {
    var tableName = "table";
    var previewTable = new DatasetPreviewTable().columns(List.of(new ColumnModel().name("test")));

    mockDataset();
    when(externalSystemService.previewTable(dataset.storageSourceId(), tableName, 30))
        .thenReturn(previewTable);
    assertThat(datasetService.getDatasetPreview(datasetId, tableName), is(previewTable));
  }

  @Test
  void exportDataset() {
    UUID workspaceId = UUID.randomUUID();
    mockDataset();
    datasetService.exportDataset(datasetId, workspaceId);
    verify(externalSystemService)
        .exportToWorkspace(dataset.storageSourceId(), workspaceId.toString());
  }
}
