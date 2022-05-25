package scripts.client;

import bio.terra.rawls.client.ApiClient;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.Objects;

public class RawlsClient extends ApiClient {

  boolean deleteWorkspaceWorkaround;

  @Override
  public String selectHeaderAccept(String[] accepts) {
    // This workaround is necessary because the rawls openAPI spec doesn't match the endpoint's
    // behavior. Without this change, calling deleteWorkspace() will fail with a 406 status.
    if (deleteWorkspaceWorkaround) {
      return "text/plain";
    }
    return super.selectHeaderAccept(accepts);
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
    setBasePath(Objects.requireNonNull(server.workspaceManagerUri, "Rawls URI required"));

    if (testUser != null) {
      GoogleCredentials userCredential =
          AuthenticationUtils.getDelegatedUserCredential(
              testUser, AuthenticationUtils.userLoginScopes);
      var accessToken = AuthenticationUtils.getAccessToken(userCredential);
      if (accessToken != null) {
        setAccessToken(accessToken.getTokenValue());
      }
    }
  }

  public WorkspacesApi createWorkspacesApi() {
    return new WorkspacesApi(this);
  }
}
