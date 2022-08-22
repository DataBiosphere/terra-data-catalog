package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.auth.OAuth;
import org.junit.jupiter.api.Test;

class RawlsClientTest {

  private static final String BASE_PATH = "base path";
  private static final String TOKEN = "token";
  private static final String AUTH_NAME = "googleoauth";

  private final RawlsClient client = new RawlsClient(new RawlsConfiguration(BASE_PATH));

  @Test
  void testApis() {
    var user =
        new AuthenticatedUserRequest.Builder()
            .setEmail("")
            .setSubjectId("")
            .setToken(TOKEN)
            .build();

    ApiClient workspacesClient = client.workspacesApi(user).getApiClient();
    validateClient(workspacesClient, TOKEN);

    ApiClient entitiesClient = client.entitiesApi(user).getApiClient();
    validateClient(entitiesClient, TOKEN);

    var statusClient = client.statusApi().getApiClient();
    validateClient(statusClient, null);

    assertThat(statusClient.getHttpClient(), is(workspacesClient.getHttpClient()));
  }

  private static void validateClient(ApiClient client, String token) {
    assertThat(client.getBasePath(), is(BASE_PATH));
    OAuth oauth = (OAuth) client.getAuthentication(AUTH_NAME);
    assertThat(oauth.getAccessToken(), is(token));
  }
}