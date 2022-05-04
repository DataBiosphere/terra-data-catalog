package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.StorageSystem;
import bio.terra.datarepo.api.JobsApi;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.model.JobModel;
import bio.terra.datarepo.model.PolicyMemberRequest;
import bio.terra.datarepo.model.SnapshotRequestContentsModel;
import bio.terra.datarepo.model.SnapshotRequestModel;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.CatalogClient;
import scripts.client.DatarepoClient;

public class DatasetPermissionOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(DatasetPermissionOperations.class);
  private static final String TEST_DATASET_NAME = "CatalogTestDataset";
  private static final String ADMIN_EMAIL = "datacatalogadmin@test.firecloud.org";
  private static final String USER_EMAIL = "datacataloguser@test.firecloud.org";

  private TestUserSpecification adminUser;
  private TestUserSpecification regularUser;

  // TDR APIs
  private SnapshotsApi adminSnapshotsApi;
  private SnapshotsApi userSnapshotsApi;
  private final List<UUID> snapshotIds = new ArrayList<>();
  private UUID defaultProfileId;
  private UUID adminTestSnapshotId;

  // Catalog APis
  private final List<UUID> datasetIds = new ArrayList<>();
  private DatasetsApi adminDatasetsApi;
  private DatasetsApi userDatasetsApi;
  private UUID adminTestDatasetId;
  private UUID userTestSnapshotId;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    for (TestUserSpecification testUser : testUsers) {
      if (testUser.userEmail.equals(ADMIN_EMAIL)) {
        adminUser = testUser;
      }
      if (testUser.userEmail.equals(USER_EMAIL)) {
        regularUser = testUser;
      }
    }
    assertNotNull(adminUser);
    assertNotNull(regularUser);

    DatarepoClient adminDatarepoClient = new DatarepoClient(server, adminUser);
    adminSnapshotsApi = new SnapshotsApi(adminDatarepoClient);
    userSnapshotsApi = new SnapshotsApi(new DatarepoClient(server, regularUser));
    defaultProfileId =
        new bio.terra.datarepo.api.DatasetsApi(adminDatarepoClient)
            .enumerateDatasets(null, null, null, null, TEST_DATASET_NAME, null)
            .getItems()
            .get(0)
            .getDefaultProfileId();
    adminDatasetsApi = new DatasetsApi(new CatalogClient(server, adminUser));
    userDatasetsApi = new DatasetsApi(new CatalogClient(server, regularUser));
    adminTestSnapshotId = createSnapshot(adminSnapshotsApi);
    userTestSnapshotId = createSnapshot(userSnapshotsApi);
    adminTestDatasetId = adminCreateDataset(datasetRequestForSnapshot(adminTestSnapshotId));
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    testReaderPermissions();
    testDiscovererPermissions();
    testNoPermissions();
    testAdminPermissionsOnUserSnapshot();
  }

  private void testReaderPermissions() throws Exception {
    setTestSnapshotPermissionForRegularUser("reader");
    // User cannot create a catalog entry on snapshot when user only has reader access on snapshot
    CreateDatasetRequest request = datasetRequestForSnapshot(adminTestSnapshotId);
    // note if this assertion fails we'll leave behind a stray dataset in the db
    assertThrows(ApiException.class, () -> userDatasetsApi.createDataset(request));
    assertThat(
        userDatasetsApi.getApiClient().getStatusCode(),
        is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));

    // verify the user can still access data though
    userDatasetsApi.getDataset(adminTestDatasetId);
    assertThat(userDatasetsApi.getApiClient().getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
  }

  private void testDiscovererPermissions() throws Exception {
    setTestSnapshotPermissionForRegularUser("discoverer");
    // User cannot create a catalog entry on snapshot when user only has discoverer access on
    // snapshot
    CreateDatasetRequest request = datasetRequestForSnapshot(adminTestSnapshotId);
    // note if this assertion fails we'll leave behind a stray dataset in the db
    assertThrows(ApiException.class, () -> userDatasetsApi.createDataset(request));
    assertThat(
        userDatasetsApi.getApiClient().getStatusCode(),
        is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));

    // verify the user cannot preview data either
    assertThrows(
        ApiException.class, () -> userDatasetsApi.listDatasetPreviewTables(adminTestDatasetId));
    assertThat(
        userDatasetsApi.getApiClient().getStatusCode(),
        is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
  }

  private void testNoPermissions() throws Exception {
    clearTestSnapshotPermissions();
    // verify the user cannot access dataset without any permissions
    assertThrows(ApiException.class, () -> userDatasetsApi.getDataset(adminTestDatasetId));
    assertThat(
        userDatasetsApi.getApiClient().getStatusCode(),
        is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
  }

  private void testAdminPermissionsOnUserSnapshot() throws Exception {
    // Verify admin can create a dataset on the user snapshot
    CreateDatasetRequest request = datasetRequestForSnapshot(userTestSnapshotId);
    var datasetId = adminCreateDataset(request);

    // But admin cannot access the underlying preview data
    assertThrows(Exception.class, () -> adminDatasetsApi.listDatasetPreviewTables(datasetId));
    assertThat(
        adminDatasetsApi.getApiClient().getStatusCode(),
        is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));
  }

  private void setTestSnapshotPermissionForRegularUser(String policy) throws Exception {
    // first clear the shared snapshot of policy state caused by previous tests
    clearTestSnapshotPermissions();
    adminSnapshotsApi.addSnapshotPolicyMember(
        adminTestSnapshotId, policy, new PolicyMemberRequest().email(regularUser.userEmail));
  }

  private void clearTestSnapshotPermissions() throws Exception {
    for (String policy : List.of("reader", "discoverer")) {
      adminSnapshotsApi.deleteSnapshotPolicyMember(
          adminTestSnapshotId, policy, regularUser.userEmail);
    }
  }

  private CreateDatasetRequest datasetRequestForSnapshot(UUID snapshotId) {
    final String metadata = """
        {"name": "test"}""";
    return new CreateDatasetRequest()
        .catalogEntry(metadata)
        .storageSourceId(snapshotId.toString())
        .storageSystem(StorageSystem.TDR);
  }

  private UUID adminCreateDataset(CreateDatasetRequest request) throws Exception {
    var datasetId = adminDatasetsApi.createDataset(request).getId();
    assertThat(adminDatasetsApi.getApiClient().getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
    datasetIds.add(datasetId);
    return datasetId;
  }

  private UUID createSnapshot(SnapshotsApi snapshotsApi) throws Exception {
    assertNotNull(defaultProfileId);
    var request =
        new SnapshotRequestModel()
            .name("catalog_integration_test_" + System.currentTimeMillis())
            .description("catalog test snapshot")
            .profileId(defaultProfileId)
            .addContentsItem(
                new SnapshotRequestContentsModel()
                    .datasetName(TEST_DATASET_NAME)
                    .mode(SnapshotRequestContentsModel.ModeEnum.BYFULLVIEW));
    String createJobId = snapshotsApi.createSnapshot(request).getId();

    ApiClient datarepoClient = snapshotsApi.getApiClient();
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
    UUID snapshotId = UUID.fromString((String) result.get("id"));
    log.info("created snapshot " + snapshotId);
    snapshotIds.add(snapshotId);
    return snapshotId;
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    for (var datasetId : datasetIds) {
      adminDatasetsApi.deleteDataset(datasetId);
      log.info("deleted dataset " + datasetId);
    }
    for (var snapshotId : snapshotIds) {
      try {
        adminSnapshotsApi.deleteSnapshot(snapshotId);
      } catch (Exception e) {
        userSnapshotsApi.deleteSnapshot(snapshotId);
      } finally {
        log.info("deleted snapshot " + snapshotId);
      }
    }
  }
}
