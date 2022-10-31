package bio.terra.catalog.datarepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.common.iam.BearerToken;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.auth.OAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatarepoClientTest {

  private static final String BASE_PATH = "basepath";
  private static final String TOKEN = "token";
  private static final String AUTH_NAME = "googleoauth";

  @Mock private BearerToken bearerToken;

  private DatarepoClient client;

  @BeforeEach
  void beforeEach() {
    when(bearerToken.getToken()).thenReturn(TOKEN);
    client = new DatarepoClient(new DatarepoConfiguration(BASE_PATH), bearerToken);
  }

  @Test
  void testApis() {
    var snapshotsClient = client.snapshotsApi().getApiClient();
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
