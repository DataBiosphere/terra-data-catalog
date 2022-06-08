package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.client.auth.OAuth;
import org.junit.jupiter.api.Test;

class RawlsClientTest {

  private static final String BASE_PATH = "basepath";
  private static final String TOKEN = "token";
  private static final String AUTH_NAME = "authorization";

  private final RawlsClient client = new RawlsClient(new RawlsConfiguration(BASE_PATH));

  @Test
  void testApis() {
    var user =
        new AuthenticatedUserRequest.Builder()
            .setEmail("")
            .setSubjectId("")
            .setToken(TOKEN)
            .build();
    var workspacesClient = client.workspacesApi(user).getApiClient();
    assertThat(workspacesClient.getBasePath(), is(BASE_PATH));

    OAuth oauth = (OAuth) workspacesClient.getAuthentication(AUTH_NAME);
    assertNotNull(oauth);
    assertThat(oauth.getAccessToken(), is(TOKEN));

    var unauthClient = client.statusApi().getApiClient();
    assertThat(unauthClient.getBasePath(), is(BASE_PATH));
    oauth = (OAuth) unauthClient.getAuthentication(AUTH_NAME);
    assertNotNull(oauth);
    assertThat(oauth.getAccessToken(), nullValue());

    assertThat(unauthClient.getHttpClient(), is(workspacesClient.getHttpClient()));
  }
}
