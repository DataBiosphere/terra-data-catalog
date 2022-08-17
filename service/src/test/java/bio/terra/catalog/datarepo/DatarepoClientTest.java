package bio.terra.catalog.datarepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.auth.OAuth;
import org.junit.jupiter.api.Test;

class DatarepoClientTest {

  private static final String BASE_PATH = "basepath";
  private static final String TOKEN = "token";
  private static final String AUTH_NAME = "googleoauth";

  private final DatarepoClient client = new DatarepoClient(new DatarepoConfiguration(BASE_PATH));

  @Test
  void testApis() {
    var user =
        new AuthenticatedUserRequest.Builder()
            .setEmail("")
            .setSubjectId("")
            .setToken(TOKEN)
            .build();

    var snapshotsClient = client.snapshotsApi(user).getApiClient();
    validateClient(snapshotsClient, TOKEN);

    var unauthClient = client.unauthenticatedApi().getApiClient();
    validateClient(unauthClient, null);

    assertThat(unauthClient.getHttpClient(), is(snapshotsClient.getHttpClient()));
  }

  private static void validateClient(ApiClient client, String token) {
    assertThat(client.getBasePath(), is(BASE_PATH));
    OAuth oauth = (OAuth) client.getAuthentication(AUTH_NAME);
    assertThat(oauth.getAccessToken(), is(token));
  }
}
