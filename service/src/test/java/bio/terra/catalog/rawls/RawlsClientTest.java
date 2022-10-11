package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.iam.User;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.auth.OAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RawlsClientTest {

  private static final String BASE_PATH = "base path";
  private static final String TOKEN = "token";
  private static final String AUTH_NAME = "googleoauth";
  @Mock private User user;

  private RawlsClient client;

  @BeforeEach
  void beforeEach() {
    when(user.getToken()).thenReturn(TOKEN);
    client = new RawlsClient(new RawlsConfiguration(BASE_PATH), user);
  }

  @Test
  void testApis() {
    ApiClient workspacesClient = client.workspacesApi().getApiClient();
    validateClient(workspacesClient, TOKEN);

    ApiClient entitiesClient = client.entitiesApi().getApiClient();
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
