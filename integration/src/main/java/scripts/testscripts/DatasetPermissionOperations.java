package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.StorageSystem;
import bio.terra.datarepo.api.JobsApi;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.model.JobModel;
import bio.terra.datarepo.model.PolicyMemberRequest;
import bio.terra.datarepo.model.SnapshotRequestContentsModel;
import bio.terra.datarepo.model.SnapshotRequestModel;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.CatalogClient;
import scripts.client.DatarepoClient;

public class DatasetPermissionOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(DatasetPermissionOperations.class);
  private static final String TEST_DATASET_NAME = "CatalogTestDataset";

  private TestUserSpecification adminUser;
  private TestUserSpecification regularUser;

  // TDR APIs
  private SnapshotsApi adminSnapshotsApi;
  private List<UUID> snapshotIds = new ArrayList<>();
  private UUID defaultProfileId;

  // Catalog APis
  private List<UUID> datasetIds = new ArrayList<>();
  private CatalogClient adminClient;
  private DatasetsApi adminDatasetsApi;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    for (TestUserSpecification testUser : testUsers) {
      if ("datacatalogadmin@test.firecloud.org".equals(testUser.userEmail)) {
        adminUser = testUser;
      }
      if ("datacataloguser@test.firecloud.org".equals(testUser.userEmail)) {
        regularUser = testUser;
      }
    }
    var datarepoClient = new DatarepoClient(server, adminUser);
    adminSnapshotsApi = new SnapshotsApi(datarepoClient);
    defaultProfileId =
        new bio.terra.datarepo.api.DatasetsApi(datarepoClient)
            .enumerateDatasets(null, null, null, null, TEST_DATASET_NAME, null)
            .getItems()
            .get(0)
            .getDefaultProfileId();
    adminClient = new CatalogClient(server, adminUser);
    adminDatasetsApi = new DatasetsApi(adminClient);
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    DatasetsApi userDatasetsApi = new DatasetsApi(new CatalogClient(server, regularUser));

    testReaderPermissions(userDatasetsApi);
    // TODO: for the discoverer test, the actual behavior does not match ticket description
    // testDiscovererPermissions(userDatasetsApi);
    testNoPermissions(userDatasetsApi);
    testAdminPermissionsOnUserSnapshot(userDatasetsApi);
  }

  private static final String METADATA = """
      {"name": "test"}""";

  private void testReaderPermissions(DatasetsApi userDatasetsApi) throws Exception {
    UUID snapshotId = createSnapshot(adminUser);
    adminSnapshotsApi.addSnapshotPolicyMember(
        snapshotId, "reader", new PolicyMemberRequest().email(regularUser.userEmail));
    // Try to create a catalog entry on snapshot when user only has reader access on snapshot
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA)
            .storageSourceId(snapshotId.toString())
            .storageSystem(StorageSystem.TDR);
    try {
      userDatasetsApi.createDataset(request);
      fail("Expected exception was not thrown");
    } catch (ApiException e) {
      assertThat(
          userDatasetsApi.getApiClient().getStatusCode(),
          is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
    }

    // Create a catalog entry as admin and verify the user can access data
    var datasetId = adminCreateDataset(request);
    userDatasetsApi.getDataset(datasetId);
    assertThat(userDatasetsApi.getApiClient().getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
  }

  private void testDiscovererPermissions(DatasetsApi userDatasetsApi) throws Exception {
    UUID snapshotId = createSnapshot(adminUser);
    adminSnapshotsApi.addSnapshotPolicyMember(
        snapshotId, "discoverer", new PolicyMemberRequest().email(regularUser.userEmail));
    // Try to create a catalog entry on snapshot when user only has discoverer access on snapshot
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA)
            .storageSourceId(snapshotId.toString())
            .storageSystem(StorageSystem.TDR);
    try {
      userDatasetsApi.createDataset(request);
      fail("Expected exception was not thrown");
    } catch (ApiException e) {
      assertThat(
          userDatasetsApi.getApiClient().getStatusCode(),
          is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
    }
    // Create a catalog entry as admin and verify the user cannot access data either
    var datasetId = adminCreateDataset(request);
    try {
      userDatasetsApi.getDataset(datasetId);
      fail("Expected exception was not thrown");
    } catch (ApiException e) {
      assertThat(
          userDatasetsApi.getApiClient().getStatusCode(),
          is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
    }
  }

  private void testNoPermissions(DatasetsApi userDatasetsApi) throws Exception {
    UUID snapshotId = createSnapshot(adminUser);
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA)
            .storageSourceId(snapshotId.toString())
            .storageSystem(StorageSystem.TDR);
    // Create a catalog entry as admin and verify the user cannot access data
    var datasetId = adminCreateDataset(request);
    try {
      userDatasetsApi.getDataset(datasetId);
      fail("Expected exception was not thrown");
    } catch (ApiException e) {
      assertThat(
          userDatasetsApi.getApiClient().getStatusCode(),
          is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
    }
  }

  private void testAdminPermissionsOnUserSnapshot(DatasetsApi userDatasetsApi) throws Exception {
    UUID snapshotId = createSnapshot(regularUser);
    var request =
        new CreateDatasetRequest()
            .catalogEntry(METADATA)
            .storageSourceId(snapshotId.toString())
            .storageSystem(StorageSystem.TDR);
    // Verify admin can create a dataset on the user snapshot
    var datasetId = adminCreateDataset(request);
    // But admin cannot access the underlying user snapshot
    try {
      adminSnapshotsApi.retrieveSnapshot(snapshotId, Collections.emptyList());
      fail("Expected exception was not thrown");
    } catch (Exception e) {
      assertThat(
          adminSnapshotsApi.getApiClient().getStatusCode(),
          is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
    }
  }

  private UUID adminCreateDataset(CreateDatasetRequest request) throws Exception {
    var datasetId = adminDatasetsApi.createDataset(request).getId();
    assertThat(adminDatasetsApi.getApiClient().getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
    datasetIds.add(datasetId);
    return datasetId;
  }

  private UUID createSnapshot(TestUserSpecification user) throws Exception {
    var datarepoClient = new DatarepoClient(server, user);
    var snapshotsApi = new SnapshotsApi(datarepoClient);
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
    UUID snapshotId = UUID.fromString((String) result.get("id"));
    log.info("created snapshot " + snapshotId);
    snapshotIds.add(snapshotId);
    return snapshotId;
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    if (!snapshotIds.isEmpty()) {
      for (var snapshotId : snapshotIds) {
        try {
          adminSnapshotsApi.deleteSnapshot(snapshotId);
        } catch (Exception e) {
          var datarepoClient = new DatarepoClient(server, regularUser);
          var snapshotsApi = new SnapshotsApi(datarepoClient);
          snapshotsApi.deleteSnapshot(snapshotId);
        } finally {
          log.info("deleted snapshot " + snapshotId);
        }
      }
    }
    if (!datasetIds.isEmpty()) {
      for (var datasetId : datasetIds) {
        adminDatasetsApi.deleteDataset(datasetId);
        log.info("deleted dataset " + datasetId);
      }
    }
  }
}
