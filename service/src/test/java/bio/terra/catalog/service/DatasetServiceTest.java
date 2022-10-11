package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import bio.terra.common.iam.AuthenticatedUserRequest;
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

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

  private DatasetService datasetService;

  @Mock private DatarepoService datarepoService;

  @Mock private RawlsService rawlsService;

  @Mock private ExternalSystemService externalSystemService;

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
            externalSystemService,
            samService,
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
            workspaceSourceId,
            new StorageSystemInformation().datasetAccessLevel(DatasetAccessLevel.OWNER));
    var idToRole =
        Map.of(
            sourceId, new StorageSystemInformation().datasetAccessLevel(DatasetAccessLevel.OWNER));
    when(datarepoService.getObjects(user)).thenReturn(idToRole);
    when(rawlsService.getObjects(user)).thenReturn(workspaces);
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
  void listDatasetsWithPhsId() {
    String phsId = "1234";
    var idToRole =
        Map.of(
            sourceId,
            new StorageSystemInformation()
                .datasetAccessLevel(DatasetAccessLevel.OWNER)
                .phsId(phsId));
    when(datarepoService.getObjects(user)).thenReturn(idToRole);
    when(rawlsService.getObjects(user)).thenReturn(Map.of());
    when(datasetDao.find(StorageSystem.TERRA_WORKSPACE, Set.of())).thenReturn(List.of());
    when(datasetDao.find(StorageSystem.TERRA_DATA_REPO, idToRole.keySet()))
        .thenReturn(List.of(tdrDataset));
    ObjectNode tdrJson = (ObjectNode) datasetService.listDatasets(user).getResult().get(0);
    assertThat(tdrJson.get("phsId").asText(), is(phsId));
    assertTrue(tdrJson.has("requestAccessURL"));
  }

  @Test
  void listDatasetsUsingAdminPermissions() {
    Map<String, StorageSystemInformation> workspaces = Map.of();
    var datasets =
        Map.of(
            sourceId, new StorageSystemInformation().datasetAccessLevel(DatasetAccessLevel.OWNER));
    when(datarepoService.getObjects(user)).thenReturn(datasets);
    when(rawlsService.getObjects(user)).thenReturn(workspaces);
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
    when(externalSystemService.getRole(user, dataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
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
    when(externalSystemService.getRole(user, dataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
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
  void testUpdateMetadataWithInvalidUser() {
    mockDataset();
    when(externalSystemService.getRole(user, dataset.storageSourceId()))
        .thenReturn(DatasetAccessLevel.NO_ACCESS);
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
  void testCreateDatasetAdmin() {
    when(samService.hasGlobalAction(user, SamAction.CREATE_METADATA)).thenReturn(true);
    when(datasetDao.create(dataset)).thenReturn(dataset);
    DatasetId id =
        datasetService.createDataset(
            user, dataset.storageSystem(), dataset.storageSourceId(), dataset.metadata());
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
    mockDataset();
    when(externalSystemService.getPreviewTables(user, tdrDataset.storageSourceId()))
        .thenReturn(tables);
    assertThat(datasetService.listDatasetPreviewTables(user, datasetId), is(response));
  }

  @Test
  void getDatasetPreviewTable() {
    var tableName = "table";
    var previewTable = new DatasetPreviewTable().columns(List.of(new ColumnModel().name("test")));

    mockDataset();
    when(externalSystemService.previewTable(user, dataset.storageSourceId(), tableName, 30))
        .thenReturn(previewTable);
    assertThat(datasetService.getDatasetPreview(user, datasetId, tableName), is(previewTable));
  }

  @Test
  void exportDataset() {
    UUID workspaceId = UUID.randomUUID();
    mockDataset();
    datasetService.exportDataset(user, datasetId, workspaceId);
    verify(externalSystemService)
        .exportToWorkspace(user, dataset.storageSourceId(), workspaceId.toString());
  }
}
