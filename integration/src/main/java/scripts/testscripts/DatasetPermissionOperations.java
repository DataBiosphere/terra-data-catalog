package scripts.testscripts;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.datarepo.api.JobsApi;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.model.JobModel;
import bio.terra.datarepo.model.SnapshotRequestContentsModel;
import bio.terra.datarepo.model.SnapshotRequestModel;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.DatarepoClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DatasetPermissionOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(DatasetPermissionOperations.class);
  private static final String TEST_DATASET_NAME = "CatalogTestDataset";

  private TestUserSpecification adminUser;
  private TestUserSpecification user;

  // TDR APIs
  private SnapshotsApi snapshotsApi;
  private UUID snapshotId;
  private DatarepoClient datarepoClient;
  private UUID defaultProfileId;

  // Catalog APis
  private UUID datasetId;
  private DatasetsApi datasetsApi;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    for (TestUserSpecification testUser : testUsers) {
      if ("datacatalogadmin@test.firecloud.org".equals(testUser.userEmail)) {
        adminUser = testUser;
      }
      if ("datacataloguser@test.firecloud.org".equals(testUser.userEmail)) {
        user = testUser;
      }
    }
    datarepoClient = new DatarepoClient(server, adminUser);
    defaultProfileId =
        new bio.terra.datarepo.api.DatasetsApi(datarepoClient)
            .enumerateDatasets(null, null, null, null, TEST_DATASET_NAME, null)
            .getItems()
            .get(0)
            .getDefaultProfileId();
  }

  private void createSnapshot() throws Exception{
    snapshotsApi = new SnapshotsApi(datarepoClient);
    var request =
        new SnapshotRequestModel()
            .name("catalog_integration_test_" + System.currentTimeMillis())
            .description("catalog test snapshot")
            .profileId(defaultProfileId)
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
}
