package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.catalog.client.ApiException;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.StorageSystem;
import bio.terra.rawls.model.WorkspaceACLUpdate;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.CatalogClient;
import scripts.client.RawlsClient;

public class WorkspacePermissionOperations extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(WorkspacePermissionOperations.class);
  private static final String ADMIN_EMAIL = "datacatalogadmin@test.firecloud.org";
  private static final String USER_EMAIL = "datacataloguser@test.firecloud.org";

  private TestUserSpecification adminUser;
  private TestUserSpecification regularUser;

  private RawlsClient adminRawlsClient;
  private RawlsClient userRawlsClient;
  private WorkspaceDetails adminTestWorkspace;
  private WorkspaceDetails userTestWorkspace;

  // Catalog APis
  private final List<UUID> datasetIds = new ArrayList<>();
  private DatasetsApi adminDatasetsApi;
  private DatasetsApi userDatasetsApi;
  private UUID adminTestDatasetId;

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

    adminRawlsClient = new RawlsClient(server, adminUser);
    userRawlsClient = new RawlsClient(server, regularUser);
    adminDatasetsApi = new DatasetsApi(new CatalogClient(server, adminUser));
    userDatasetsApi = new DatasetsApi(new CatalogClient(server, regularUser));
    adminTestWorkspace = adminRawlsClient.createTestWorkspace();
    userTestWorkspace = userRawlsClient.createTestWorkspace();
    adminTestDatasetId =
        adminCreateDataset(datasetRequestForWorkspace(adminTestWorkspace.getWorkspaceId()));
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    testReaderPermissions();
    testNoPermissions();
    testAdminPermissionsOnUserSnapshot();
  }

  private void testReaderPermissions() throws Exception {
    setTestWorkspacePermissionForRegularUser(WorkspaceAccessLevel.READER.getValue());
    // User cannot create a catalog entry on snapshot when user only has reader access on snapshot
    CreateDatasetRequest request = datasetRequestForWorkspace(adminTestWorkspace.getWorkspaceId());
    // note if this assertion fails we'll leave behind a stray dataset in the db
    assertThrows(ApiException.class, () -> userDatasetsApi.createDataset(request));
    assertThat(
        userDatasetsApi.getApiClient().getStatusCode(),
        is(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED));

    // verify the user can still access data though
    userDatasetsApi.getDataset(adminTestDatasetId);
    assertThat(userDatasetsApi.getApiClient().getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
  }

  private void testNoPermissions() throws Exception {
    clearTestWorkspacePermissions();
    // verify the user cannot access dataset without any permissions
    assertThrows(ApiException.class, () -> userDatasetsApi.getDataset(adminTestDatasetId));
    assertThat(
        userDatasetsApi.getApiClient().getStatusCode(), is(HttpStatusCodes.STATUS_CODE_NOT_FOUND));
  }
  // TODO (DC-446): Fix with https://broadworkbench.atlassian.net/browse/DC-446
  //  private void testNoAccessPermissions() throws Exception {
  //    setTestWorkspacePermissionForRegularUser(WorkspaceAccessLevel.NO_ACCESS.getValue());
  //    // User cannot create a catalog entry on Workspace when user has "No Access" on a workspace
  //    CreateDatasetRequest request =
  // datasetRequestForWorkspace(adminTestWorkspace.getWorkspaceId());
  //    // note if this assertion fails we'll leave behind a stray dataset in the db
  //    assertThrows(ApiException.class, () -> userDatasetsApi.createDataset(request));
  //    assertThat(
  //        userDatasetsApi.getApiClient().getStatusCode(),
  // is(HttpStatusCodes.STATUS_CODE_NOT_FOUND));
  //    // but the user can get datasets
  //    userDatasetsApi.getDataset(adminTestDatasetId);
  //    assertThat(userDatasetsApi.getApiClient().getStatusCode(),
  // is(HttpStatusCodes.STATUS_CODE_OK));
  //  }

  private void testAdminPermissionsOnUserSnapshot() throws Exception {
    // Verify admin can create a dataset on the user snapshot
    CreateDatasetRequest request = datasetRequestForWorkspace(userTestWorkspace.getWorkspaceId());
    adminCreateDataset(request);
  }

  private void setTestWorkspacePermissionForRegularUser(String policy) throws Exception {
    // first clear the shared snapshot of policy state caused by previous tests
    adminRawlsClient.updateWorkspaceAcl(
        List.of(new WorkspaceACLUpdate().email(regularUser.userEmail).accessLevel(policy)),
        adminTestWorkspace);
  }

  private void clearTestWorkspacePermissions() throws Exception {
    log.info(
        "Clearing acl permissions for {}/{}",
        adminTestWorkspace.getNamespace(),
        adminTestWorkspace.getName());
    setTestWorkspacePermissionForRegularUser(WorkspaceAccessLevel.NO_ACCESS.getValue());
  }

  private CreateDatasetRequest datasetRequestForWorkspace(String workspaceId) {
    final String metadata = """
        {"name": "test"}""";
    return new CreateDatasetRequest()
        .catalogEntry(metadata)
        .storageSourceId(workspaceId)
        .storageSystem(StorageSystem.WKS);
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
      log.info("deleted dataset {}", datasetId);
    }
    if (adminTestWorkspace != null) {
      log.info(
          "deleting workspace {}/{}",
          adminTestWorkspace.getNamespace(),
          adminTestWorkspace.getName());
      adminRawlsClient.deleteWorkspace(adminTestWorkspace);
    }
    if (userTestWorkspace != null) {
      log.info(
          "deleting workspace {}/{}",
          userTestWorkspace.getNamespace(),
          userTestWorkspace.getName());
      userRawlsClient.deleteWorkspace(userTestWorkspace);
    }
  }
}
