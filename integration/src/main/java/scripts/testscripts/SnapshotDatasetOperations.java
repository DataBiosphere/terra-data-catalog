package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.ColumnModel;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.StorageSystem;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.datarepo.api.JobsApi;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.model.JobModel;
import bio.terra.datarepo.model.SnapshotRequestContentsModel;
import bio.terra.datarepo.model.SnapshotRequestModel;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.CatalogClient;
import scripts.client.DatarepoClient;

/**
 * A test for dataset operations using the catalog service endpoints, with TDR snapshots as the
 * dataset source.
 *
 * <p>Create, Read, Update, Delete are tested.
 */
public class SnapshotDatasetOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(SnapshotDatasetOperations.class);
  private static final String TEST_DATASET_NAME = "CatalogTestDataset";
  private static final String ADMIN_EMAIL = "datacatalogadmin@test.firecloud.org";

  // TDR APIs
  private SnapshotsApi snapshotsApi;
  private UUID snapshotId;

  // Catalog APis
  private UUID datasetId;
  private DatasetsApi datasetsApi;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    DatarepoClient datarepoClient = null;
    for (TestUserSpecification testUser : testUsers) {
      if (testUser.userEmail.equals(ADMIN_EMAIL)) {
        datarepoClient = new DatarepoClient(server, testUser);
      }
    }
    assertNotNull(datarepoClient);
    var datasetsApi = new bio.terra.datarepo.api.DatasetsApi(datarepoClient);
    var dataset =
        datasetsApi
            .enumerateDatasets(null, null, null, null, TEST_DATASET_NAME, null)
            .getItems()
            .get(0);

    snapshotsApi = new SnapshotsApi(datarepoClient);
    var request =
        new SnapshotRequestModel()
            .name("catalog_integration_test_" + System.currentTimeMillis())
            .description("catalog test snapshot")
            .profileId(dataset.getDefaultProfileId())
            .addContentsItem(
                new SnapshotRequestContentsModel()
                    .datasetName(TEST_DATASET_NAME)
                    .mode(SnapshotRequestContentsModel.ModeEnum.BYFULLVIEW));
    var createJobId = snapshotsApi.createSnapshot(request).getId();

    var jobApi = new JobsApi(datarepoClient);
    JobModel job;
    do {
      job = jobApi.retrieveJob(createJobId);
      TimeUnit.SECONDS.sleep(5);
    } while (job.getJobStatus() == JobModel.JobStatusEnum.RUNNING);

    if (job.getJobStatus() != JobModel.JobStatusEnum.SUCCEEDED) {
      throw new RuntimeException("Create snapshot failed: " + job);
    }
    @SuppressWarnings("unchecked")
    Map<Object, Object> result = (Map<Object, Object>) jobApi.retrieveJobResult(createJobId);
    snapshotId = UUID.fromString((String) result.get("id"));
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

    crudUserJourney(client);
    previewUserJourney(client);
  }

  private void previewUserJourney(CatalogClient client) throws ApiException {
    // Given a snapshot, create a catalog entry.
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA_1)
            .storageSourceId(snapshotId.toString())
            .storageSystem(StorageSystem.TDR);
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

  private void crudUserJourney(CatalogClient client) throws ApiException {
    // Given a snapshot, create a catalog entry.
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA_1)
            .storageSourceId(snapshotId.toString())
            .storageSystem(StorageSystem.TDR);
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
        @SuppressWarnings("unchecked")
        List<Object> roles = (List<Object>) dataset.get("roles");
        assertThat(roles, hasItem("steward"));
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
      snapshotsApi.deleteSnapshot(snapshotId);
      log.info("deleted snapshot " + snapshotId);
    }
  }
}
