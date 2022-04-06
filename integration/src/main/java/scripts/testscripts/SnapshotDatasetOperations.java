package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.StorageSystem;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.CatalogClient;
import scripts.client.DataRepoClient;

/**
 * A test for dataset operations using the catalog service endpoints, with TDR snapshots as the
 * dataset source.
 *
 * <p>Create, Read, Update, Delete are tested.
 */
public class SnapshotDatasetOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(SnapshotDatasetOperations.class);
  private static final String TEST_DATASET_NAME = "CatalogTestDataset";

  // TDR APIs
  private SnapshotsApi snapshotsApi;
  private UUID snapshotId;

  // Catalog APis
  private UUID datasetId;
  private DatasetsApi datasetsApi;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    DataRepoClient datarepoClient = new DataRepoClient(server, testUsers.get(0));
    var datasetsApi = new bio.terra.datarepo.api.DatasetsApi(datarepoClient);
    var dataset =
        datasetsApi
            .enumerateDatasets(null, null, null, null, TEST_DATASET_NAME, null)
            .getItems()
            .get(0);

    snapshotsApi = new SnapshotsApi(datarepoClient);
    var request =
        new SnapshotRequestModel()
            .name("pshapiro_test_1")
            .description("test snapshot")
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
    assertThat(datasets.getResult(), is(not(empty())));

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
    assertThrows(ApiException.class, () -> datasetsApi.getDataset(datasetId));
    datasetId = null;
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    if (snapshotId != null) {
      snapshotsApi.deleteSnapshot(snapshotId);
      log.info("deleted snapshot " + snapshotId);
    }
    if (datasetId != null) {
      datasetsApi.deleteDataset(datasetId);
      log.info("deleted dataset " + datasetId);
    }
  }
}
