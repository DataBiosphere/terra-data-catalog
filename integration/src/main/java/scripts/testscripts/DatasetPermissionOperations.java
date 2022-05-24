package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.StorageSystem;
import bio.terra.datarepo.model.DatasetModel;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.api.SnapshotsApi;
import scripts.api.TdrDatasetsApi;
import scripts.client.CatalogClient;
import scripts.client.DatarepoClient;

public class DatasetPermissionOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(DatasetPermissionOperations.class);
  private static final String ADMIN_EMAIL = "datacatalogadmin@test.firecloud.org";
  private static final String USER_EMAIL = "datacataloguser@test.firecloud.org";

  private TestUserSpecification adminUser;
  private TestUserSpecification regularUser;

  // TDR APIs
  private SnapshotsApi adminSnapshotsApi;
  private SnapshotsApi userSnapshotsApi;
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
    TdrDatasetsApi tdrDatasetsApi = adminDatarepoClient.datasetsApi();
    DatasetModel tdrDataset = tdrDatasetsApi.getTestDataset();
    adminDatasetsApi = new DatasetsApi(new CatalogClient(server, adminUser));
    userDatasetsApi = new DatasetsApi(new CatalogClient(server, regularUser));
    adminTestSnapshotId = adminSnapshotsApi.createTestSnapshot(tdrDataset);
    tdrDatasetsApi.addDatasetPolicyMember(tdrDataset.getId(), "custodian", regularUser.userEmail);
    userTestSnapshotId = userSnapshotsApi.createTestSnapshot(tdrDataset);
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
    // but the user can get datasets
    userDatasetsApi.getDataset(adminTestDatasetId);
    assertThat(userDatasetsApi.getApiClient().getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
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
    adminSnapshotsApi.addPolicyMember(adminTestSnapshotId, policy, regularUser.userEmail);
  }

  private void clearTestSnapshotPermissions() throws Exception {
    for (String policy : List.of("reader", "discoverer")) {
      adminSnapshotsApi.deletePolicyMember(adminTestSnapshotId, policy, regularUser.userEmail);
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

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    for (var datasetId : datasetIds) {
      adminDatasetsApi.deleteDataset(datasetId);
      log.info("deleted dataset " + datasetId);
    }
    if (adminTestSnapshotId != null) {
      adminSnapshotsApi.delete(adminTestSnapshotId);
    }
    if (userTestSnapshotId != null) {
      userSnapshotsApi.delete(userTestSnapshotId);
    }
  }
}
