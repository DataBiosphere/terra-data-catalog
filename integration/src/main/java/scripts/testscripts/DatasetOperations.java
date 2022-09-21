package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.DatasetExportRequest;
import bio.terra.catalog.model.StorageSystem;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.datarepo.model.DatasetModel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.api.SnapshotsApi;
import scripts.client.CatalogClient;
import scripts.client.DatarepoClient;
import scripts.client.RawlsClient;

/**
 * A test for dataset operations using the catalog service endpoints, with TDR snapshots as the
 * dataset source.
 *
 * <p>Create, Read, Update, Delete are tested.
 */
public class DatasetOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(DatasetOperations.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  // TDR APIs
  private SnapshotsApi snapshotsApi;
  private UUID snapshotId;

  // Rawls APIs
  private RawlsClient rawlsClient;
  private WorkspaceDetails workspaceSource;
  private WorkspaceDetails workspaceDest;

  // Catalog APis
  private UUID datasetId;
  private DatasetsApi datasetsApi;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    var user = testUsers.get(0);
    setupSnapshot(user);
    setupWorkspace(user);
  }

  private void setupWorkspace(TestUserSpecification user) throws Exception {
    rawlsClient = new RawlsClient(server, user);
    workspaceSource = rawlsClient.createTestWorkspace();
    rawlsClient.ingestData(workspaceSource);
    workspaceDest = rawlsClient.createTestWorkspace();
  }

  private void setupSnapshot(TestUserSpecification user) throws Exception {
    DatarepoClient datarepoClient = new DatarepoClient(server, user);
    DatasetModel tdrDataset = datarepoClient.datasetsApi().getTestDataset();
    snapshotsApi = new SnapshotsApi(datarepoClient);
    snapshotId = snapshotsApi.createTestSnapshot(tdrDataset);
  }

  private ObjectNode createMetadata(String name) {
    return objectMapper.createObjectNode().put("name", name);
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    var client = new CatalogClient(server, testUser);
    datasetsApi = new DatasetsApi(client);

    crudUserJourney(client, StorageSystem.TDR, snapshotId.toString());
    crudUserJourney(client, StorageSystem.WKS, workspaceSource.getWorkspaceId());

    previewUserJourney(StorageSystem.TDR, snapshotId.toString());
    previewUserJourney(StorageSystem.WKS, workspaceSource.getWorkspaceId());

    exportUserJourney(StorageSystem.WKS, workspaceSource, workspaceDest);
  }

  private void exportUserJourney(
      StorageSystem storageSystem, WorkspaceDetails workspaceSource, WorkspaceDetails workspaceDest)
      throws ApiException, bio.terra.rawls.client.ApiException {
    // Create workspace dataset
    var request =
        new CreateDatasetRequest()
            .catalogEntry(createMetadata("export-test-dataset").toString())
            .storageSourceId(workspaceSource.getWorkspaceId())
            .storageSystem(storageSystem);
    datasetId = datasetsApi.createDataset(request).getId();

    // Export workspace dataset to workspace
    var workspaceId = UUID.fromString(workspaceDest.getWorkspaceId());
    datasetsApi.exportDataset(datasetId, new DatasetExportRequest().workspaceId(workspaceId));

    // Extract workspace entity names
    Set<String> entitiesSource = rawlsClient.getWorkspaceEntities(workspaceSource);
    Set<String> entitiesDest = rawlsClient.getWorkspaceEntities(workspaceDest);

    assertEquals(entitiesSource, entitiesDest);

    // Delete catalog entry
    datasetsApi.deleteDataset(datasetId);
    datasetId = null;
  }

  private void previewUserJourney(StorageSystem storageSystem, String sourceId)
      throws ApiException {
    // Given a snapshot, create a catalog entry.
    var request =
        new CreateDatasetRequest()
            .catalogEntry(createMetadata("preview").toString())
            .storageSourceId(sourceId)
            .storageSystem(storageSystem);
    datasetId = datasetsApi.createDataset(request).getId();
    log.info("created dataset " + datasetId);

    var previewTables = datasetsApi.listDatasetPreviewTables(datasetId);
    assertThat(
        previewTables.getTables(),
        containsInAnyOrder(
            new TableMetadata().name("sample").hasData(true),
            new TableMetadata().name("participant").hasData(true)));
    var sampleTable = datasetsApi.getDatasetPreviewTable(datasetId, "sample");
    assertThat(
        sampleTable.getColumns(),
        containsInAnyOrder(
            new ColumnModel().name("sample_id"),
            new ColumnModel().name("participant_id"),
            new ColumnModel().name("files"),
            new ColumnModel().name("type")));

    assertThat(sampleTable.getRows(), hasSize(15));
    @SuppressWarnings("unchecked")
    Map<String, String> row = (Map<String, String>) sampleTable.getRows().get(0);

    assertThat(row, hasEntry(is("sample_id"), notNullValue()));

    // Delete the entry
    datasetsApi.deleteDataset(datasetId);
    datasetId = null;
  }

  private void assertDatasetValues(
      List<String> keys, JsonNode jsonOne, String name, StorageSystem storageSystem) {
    ObjectNode expectedMetadata = createMetadata(name).put("id", datasetId.toString());
    if (storageSystem.equals(StorageSystem.TDR)) {
      expectedMetadata.put("phsId", "1234");
    }
    keys.forEach(key -> assertEquals(jsonOne.get(key), expectedMetadata.get(key)));
  }

  private void crudUserJourney(CatalogClient client, StorageSystem storageSystem, String sourceId)
      throws Exception {
    // Given a snapshot, create a catalog entry.
    var request =
        new CreateDatasetRequest()
            .catalogEntry(createMetadata("crud").toString())
            .storageSourceId(sourceId)
            .storageSystem(storageSystem);
    datasetId = datasetsApi.createDataset(request).getId();
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
    assertThat(datasetId, notNullValue());
    log.info("created dataset " + datasetId);

    // Retrieve the entry
    JsonNode datasetResponse = objectMapper.readTree(datasetsApi.getDataset(datasetId));
    assertDatasetValues(List.of("name", "phsId", "id"), datasetResponse, "crud", storageSystem);
    assertNotNull(datasetResponse.get("requestAccessURL"));
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));

    // Retrieve all datasets
    var datasets = datasetsApi.listDatasets();
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
    resultHasDatasetWithRoles(datasets.getResult(), storageSystem);

    // Modify the entry
    datasetsApi.updateDataset(createMetadata("crud2").toString(), datasetId);
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_NO_CONTENT));

    // Verify modify success
    JsonNode datasetResponseTwo = objectMapper.readTree(datasetsApi.getDataset(datasetId));
    assertDatasetValues(List.of("name", "phsId", "id"), datasetResponseTwo, "crud2", storageSystem);
    assertNotNull(datasetResponseTwo.get("requestAccessURL"));
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));

    // Delete the entry
    datasetsApi.deleteDataset(datasetId);
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_NO_CONTENT));

    // Verify delete success.
    var apiException = assertThrows(ApiException.class, () -> datasetsApi.getDataset(datasetId));
    assertThat(apiException.getCode(), is(HttpStatusCodes.STATUS_CODE_NOT_FOUND));
    assertThat(apiException.getResponseBody(), containsString(datasetId.toString()));
    datasetId = null;
  }

  private void resultHasDatasetWithRoles(List<Object> datasets, StorageSystem storageSystem) {
    for (Object datasetObj : datasets) {
      @SuppressWarnings("unchecked")
      Map<Object, Object> dataset = (Map<Object, Object>) datasetObj;
      if (dataset.get("id").equals(datasetId.toString())) {
        assertThat(dataset, hasEntry(is("name"), is("test")));
        assertThat(dataset, hasEntry(is("id"), is(datasetId.toString())));
        assertThat(dataset, hasEntry(is("accessLevel"), is("owner")));
        if (storageSystem.equals(StorageSystem.TDR)) {
          assertThat(dataset, hasEntry(is("phsId"), is("1234")));
          assertNotNull(dataset.get("requestAccessURL"));
        }
        return;
      }
    }
    fail("Dataset not returned in list result, expected id " + datasetId);
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    if (datasetId != null) {
      datasetsApi.deleteDataset(datasetId);
      log.info("deleted dataset " + datasetId);
    }
    if (snapshotId != null) {
      snapshotsApi.delete(snapshotId);
      log.info("deleted snapshot " + snapshotId);
    }
    if (workspaceSource != null) {
      rawlsClient.deleteWorkspace(workspaceSource);
    }
    if (workspaceDest != null) {
      rawlsClient.deleteWorkspace(workspaceDest);
    }
  }
}
