package scripts.client;

import bio.terra.rawls.api.BillingV2Api;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.CreateRawlsV2BillingProjectFullRequest;
import bio.terra.rawls.model.WorkspaceACLUpdate;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceRequest;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawlsClient extends ApiClient {
  private static final Logger log = LoggerFactory.getLogger(RawlsClient.class);

  private static final String BILLING_ACCOUNT = "billingAccounts/00708C-45D19D-27AAFA";
  private static final List<String> BILLING_SCOPES =
      Stream.concat(
              Stream.of("https://www.googleapis.com/auth/cloud-billing"),
              AuthenticationUtils.userLoginScopes.stream())
          .toList();

  private final WorkspacesApi workspacesApi;
  private final BillingV2Api billingApi;

  private boolean deleteWorkspaceWorkaround;

  private WorkspacesApi createWorkspacesApi(String basePath, TestUserSpecification testUser)
      throws IOException {
    var workspaceApiClient =
        new ApiClient() {
          @Override
          public String selectHeaderAccept(String[] accepts) {
            // This workaround is necessary because the rawls openAPI spec doesn't match the
            // endpoint's  behavior. Without this change, calling deleteWorkspace() will fail
            // with a 406 status.
            if (deleteWorkspaceWorkaround) {
              return "text/plain";
            }
            return super.selectHeaderAccept(accepts);
          }
        };
    workspaceApiClient.setBasePath(basePath);

    GoogleCredentials userCredential =
        AuthenticationUtils.getDelegatedUserCredential(
            testUser, AuthenticationUtils.userLoginScopes);
    workspaceApiClient.setAccessToken(
        AuthenticationUtils.getAccessToken(userCredential).getTokenValue());

    return new WorkspacesApi(workspaceApiClient);
  }

  private BillingV2Api createBillingApi(String basePath, TestUserSpecification testUser)
      throws IOException {
    var billingApiClient = new ApiClient();
    billingApiClient.setBasePath(basePath);
    GoogleCredentials testRunnerCredentials =
        AuthenticationUtils.getDelegatedUserCredential(testUser, BILLING_SCOPES);
    String testRunnerAccessToken =
        AuthenticationUtils.getAccessToken(testRunnerCredentials).getTokenValue();
    billingApiClient.setAccessToken(testRunnerAccessToken);
    return new BillingV2Api(billingApiClient);
  }

  /**
   * Build the API client object for the given test user and catalog server. The test user's token
   * is always refreshed. If a test user isn't configured (e.g. when running locally), return an
   * un-authenticated client.
   *
   * @param server the server we are testing against
   * @param testUser the test user whose credentials are supplied to the API client object
   */
  public RawlsClient(ServerSpecification server, TestUserSpecification testUser)
      throws IOException {
    String basePath = Objects.requireNonNull(server.rawlsUri, "Rawls URI required");
    workspacesApi = createWorkspacesApi(basePath, testUser);
    billingApi = createBillingApi(basePath, testUser);
  }

  private String createBillingProject() throws ApiException {
    var request =
        new CreateRawlsV2BillingProjectFullRequest()
            .billingAccount(BILLING_ACCOUNT)
            .projectName("catalog_test_" + System.currentTimeMillis());
    billingApi.createBillingProjectFullV2(request);
    log.info("created billing project {}", request.getProjectName());
    return request.getProjectName();
  }

  public WorkspaceDetails createTestWorkspace() throws ApiException {
    var request =
        new WorkspaceRequest()
            .name("catalog_integration_test_" + UUID.randomUUID().toString().replace('-', '_'))
            .namespace(createBillingProject())
            .attributes(Map.of());
    var workspaceDetails = workspacesApi.createWorkspace(request);
    log.info("created workspace {}", workspaceDetails.getWorkspaceId());
    return workspaceDetails;
  }

  public void deleteWorkspace(WorkspaceDetails workspaceDetails) throws ApiException {
    try {
      deleteWorkspaceWorkaround = true;
      workspacesApi.deleteWorkspace(workspaceDetails.getNamespace(), workspaceDetails.getName());
    } finally {
      deleteWorkspaceWorkaround = false;
    }
    billingApi.deleteBillingProjectV2(workspaceDetails.getNamespace());
    log.info("deleted workspace {}", workspaceDetails.getWorkspaceId());
    log.info("deleted billing project {}", workspaceDetails.getNamespace());
  }

  public void updateWorkspaceAcl(
      List<WorkspaceACLUpdate> updates, WorkspaceDetails workspaceDetails) throws ApiException {
    workspacesApi.updateACL(
        updates, false, workspaceDetails.getNamespace(), workspaceDetails.getName());
  }
}
