package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.StorageSystem;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.datarepo.model.SnapshotRequestContentsModel;
import bio.terra.datarepo.model.SnapshotRequestModel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceRequest;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.api.SnapshotsSyncApi;
import scripts.client.CatalogClient;
import scripts.client.DatarepoClient;
import scripts.client.RawlsClient;
import scripts.client.WorkspacesApi;

/**
 * A test for dataset operations using the catalog service endpoints, with TDR snapshots as the
 * dataset source.
 *
 * <p>Create, Read, Update, Delete are tested.
 */
public class DatasetOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(DatasetOperations.class);
  private static final String TEST_DATASET_NAME = "CatalogTestDataset";

  // TDR APIs
  private SnapshotsSyncApi snapshotsApi;
  private UUID snapshotId;

  // Rawls APIs
  private WorkspacesApi workspacesApi;
  private WorkspaceDetails workspaceDetails;

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
    var rawlsClient = new RawlsClient(server, user);
    workspacesApi = rawlsClient.createWorkspacesApi();
    workspaceDetails = workspacesApi.createTestWorkspace();
  }

  private void setupSnapshot(TestUserSpecification user) throws Exception {
    DatarepoClient datarepoClient = new DatarepoClient(server, user);
    var datasetsApi = new bio.terra.datarepo.api.DatasetsApi(datarepoClient);
    var dataset =
        datasetsApi
            .enumerateDatasets(null, null, null, null, TEST_DATASET_NAME, null)
            .getItems()
            .get(0);

    snapshotsApi = new SnapshotsSyncApi(datarepoClient);
    var request =
        new SnapshotRequestModel()
            .name("catalog_integration_test_" + System.currentTimeMillis())
            .description("catalog test snapshot")
            .profileId(dataset.getDefaultProfileId())
            .addContentsItem(
                new SnapshotRequestContentsModel()
                    .datasetName(TEST_DATASET_NAME)
                    .mode(SnapshotRequestContentsModel.ModeEnum.BYFULLVIEW));
    snapshotId = snapshotsApi.synchronousCreateSnapshot(request);
    log.info("created snapshot " + snapshotId);
  }

  private static final String METADATA_1 = """
      {"name": "test"}""";

  private static final String METADATA_2 = """
      {"name": "test2"}""";

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    var client = new CatalogClient(server, testUser);
    datasetsApi = new DatasetsApi(client);

    crudUserJourney(client, StorageSystem.TDR, snapshotId.toString());
    crudUserJourney(client, StorageSystem.WKS, workspaceDetails.getWorkspaceId());

    previewUserJourney(StorageSystem.TDR, snapshotId.toString());
  }

  private void previewUserJourney(StorageSystem storageSystem, String sourceId)
      throws ApiException {
    // Given a snapshot, create a catalog entry.
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA_1)
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
            new ColumnModel().name("sample_id").arrayOf(false),
            new ColumnModel().name("participant_id").arrayOf(false),
            new ColumnModel().name("files").arrayOf(true),
            new ColumnModel().name("type").arrayOf(false)));

    assertThat(sampleTable.getRows(), hasSize(15));
    @SuppressWarnings("unchecked")
    Map<String, String> row = (Map<String, String>) sampleTable.getRows().get(0);

    assertThat(row, hasEntry(is("sample_id"), notNullValue()));

    // Delete the entry
    datasetsApi.deleteDataset(datasetId);
    datasetId = null;
  }

  private void crudUserJourney(CatalogClient client, StorageSystem storageSystem, String sourceId)
      throws ApiException {
    // Given a snapshot, create a catalog entry.
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA_1)
            .storageSourceId(sourceId)
            .storageSystem(storageSystem);
    datasetId = datasetsApi.createDataset(request).getId();
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
    assertThat(datasetId, notNullValue());
    log.info("created dataset " + datasetId);

    // Retrieve the entry
    assertThat(datasetsApi.getDataset(datasetId), is(METADATA_1));
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));

    // Retrieve all datasets
    var datasets = datasetsApi.listDatasets();
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
    resultHasDatasetWithRoles(datasets.getResult());

    // Modify the entry
    datasetsApi.updateDataset(METADATA_2, datasetId);
    assertThat(client.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_NO_CONTENT));

    // Verify modify success
    assertThat(datasetsApi.getDataset(datasetId), is(METADATA_2));
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

  private void resultHasDatasetWithRoles(List<Object> datasets) {
    for (Object datasetObj : datasets) {
      @SuppressWarnings("unchecked")
      Map<Object, Object> dataset = (Map<Object, Object>) datasetObj;
      if (dataset.get("id").equals(datasetId.toString())) {
        assertThat(dataset, hasEntry(is("name"), is("test")));
        assertThat(dataset, hasEntry(is("id"), is(datasetId.toString())));
        assertThat(dataset, hasEntry(is("accessLevel"), is("owner")));
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
      snapshotsApi.synchronousDeleteSnapshot(snapshotId);
      log.info("deleted snapshot " + snapshotId);
    }
    if (workspaceDetails != null) {
      workspacesApi.deleteWorkspace(workspaceDetails);
    }
  }
}
