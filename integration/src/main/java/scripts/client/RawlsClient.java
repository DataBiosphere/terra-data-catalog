package scripts.client;

import bio.terra.rawls.api.BillingV2Api;
import bio.terra.rawls.api.EntitiesApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.CreateRawlsV2BillingProjectFullRequest;
import bio.terra.rawls.model.Entity;
import bio.terra.rawls.model.EntityTypeMetadata;
import bio.terra.rawls.model.WorkspaceACLUpdate;
import bio.terra.rawls.model.WorkspaceAccessLevel;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jdk.connector.JdkConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawlsClient {
  private static final Logger log = LoggerFactory.getLogger(RawlsClient.class);

  private static final String BILLING_ACCOUNT = "billingAccounts/00708C-45D19D-27AAFA";
  private static final List<String> BILLING_SCOPES =
      Stream.concat(
              Stream.of("https://www.googleapis.com/auth/cloud-billing"),
              AuthenticationUtils.userLoginScopes.stream())
          .toList();

  private final WorkspacesApi workspacesApi;
  private final EntitiesApi entitiesApi;
  private final BillingV2Api billingApi;

  private boolean deleteWorkspaceWorkaround;

  private static ApiClient setUserAndScopes(
      ApiClient apiClient, String basePath, TestUserSpecification testUser, List<String> scopes)
      throws IOException {
    apiClient.setBasePath(basePath);
    GoogleCredentials userCredentials =
        AuthenticationUtils.getDelegatedUserCredential(testUser, scopes);
    String accessToken = AuthenticationUtils.getAccessToken(userCredentials).getTokenValue();
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private WorkspacesApi createWorkspacesApi(String basePath, TestUserSpecification testUser)
      throws IOException {
    var workspaceApiClient =
        new ApiClient() {
          @Override
          protected void performAdditionalClientConfiguration(ClientConfig clientConfig) {
            super.performAdditionalClientConfiguration(clientConfig);
            clientConfig.connectorProvider(new JdkConnectorProvider());
          }

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

    return new WorkspacesApi(
        setUserAndScopes(
            workspaceApiClient, basePath, testUser, AuthenticationUtils.userLoginScopes));
  }

  private BillingV2Api createBillingApi(String basePath, TestUserSpecification testUser)
      throws IOException {
    var billingApiClient = new ApiClient();
    setUserAndScopes(billingApiClient, basePath, testUser, BILLING_SCOPES);
    return new BillingV2Api(billingApiClient);
  }

  private EntitiesApi createEntitiesApi(String basePath, TestUserSpecification testUser)
      throws IOException {
    var entitiesApiClient = new ApiClient();
    setUserAndScopes(entitiesApiClient, basePath, testUser, AuthenticationUtils.userLoginScopes);
    return new EntitiesApi(entitiesApiClient);
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
    entitiesApi = createEntitiesApi(basePath, testUser);
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

  public void ingestData(WorkspaceDetails workspaceDetails) throws ApiException {
    var namespace = workspaceDetails.getNamespace();
    var name = workspaceDetails.getName();

    for (int i = 1; i <= 15; i++) {
      Entity entity = new Entity().entityType("sample").name("sample" + i);
      entity.setAttributes(
          Map.of(
              "files", List.of(1, 2, 3, 4, 5), "type", "bam", "participant_id", "participant" + i));
      entitiesApi.createEntity(entity, namespace, name);
    }
    for (int i = 1; i <= 15; i++) {
      Entity entity = new Entity().entityType("participant").name("participant" + i);
      entity.setAttributes(
          Map.of("age", String.valueOf(i + 10), "biological_sex", i % 2 == 0 ? "male" : "female"));
      entitiesApi.createEntity(entity, namespace, name);
    }
  }

  public Set<String> getWorkspaceEntities(WorkspaceDetails workspaceDetails) throws ApiException {
    Map<String, EntityTypeMetadata> entityTypeMetadataMap =
        entitiesApi.entityTypeMetadata(
            workspaceDetails.getNamespace(), workspaceDetails.getName(), true, null);
    return entityTypeMetadataMap.keySet();
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

  public void updateWorkspacePermissionForUser(
      String email, WorkspaceAccessLevel accessLevel, WorkspaceDetails workspaceDetails)
      throws ApiException {
    workspacesApi.updateACL(
        List.of(new WorkspaceACLUpdate().email(email).accessLevel(accessLevel.getValue())),
        false,
        workspaceDetails.getNamespace(),
        workspaceDetails.getName());
  }
}
