package bio.terra.catalog.datarepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.client.auth.OAuth;
import org.junit.jupiter.api.Test;

class DatarepoClientTest {

  private static final String BASE_PATH = "basepath";
  private static final String TOKEN = "token";
  private static final String AUTH_NAME = "googleoauth";

  private final DatarepoClient client;

  DatarepoClientTest() {
    client = new DatarepoClient(new DatarepoConfiguration(BASE_PATH));
  }

  @Test
  void testApis() {
    var user =
        new AuthenticatedUserRequest.Builder()
            .setEmail("")
            .setSubjectId("")
            .setToken(TOKEN)
            .build();
    var snapshotsClient = client.snapshotsApi(user).getApiClient();
    assertThat(snapshotsClient.getBasePath(), is(BASE_PATH));

    OAuth oauth = (OAuth) snapshotsClient.getAuthentication(AUTH_NAME);
    assertNotNull(oauth);
    assertThat(oauth.getAccessToken(), is(TOKEN));

    var unauthClient = client.unauthenticatedApi().getApiClient();
    assertThat(unauthClient.getBasePath(), is(BASE_PATH));
    assertThat(unauthClient.getAuthentication(AUTH_NAME), nullValue());

    assertThat(unauthClient.getHttpClient(), is(snapshotsClient.getHttpClient()));
  }
}
