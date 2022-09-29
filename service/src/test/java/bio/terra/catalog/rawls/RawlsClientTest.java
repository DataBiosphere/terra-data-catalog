package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.client.ApiClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class RawlsClientTest {

  private static final String BASE_PATH = "https://host/path";
  private static final String TOKEN = "token";
  private static final String AUTH_NAME = "googleoauth";

  private final RawlsClient client = new RawlsClient(new RawlsConfiguration(BASE_PATH));

  @Test
  void testApis() throws Exception {
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

  private static void validateClient(ApiClient client, String token) throws Exception {
    assertThat(client.getBasePath(), is(BASE_PATH));
    Map<String, String> headerParams = new HashMap<>();
    client
        .getAuthentication(AUTH_NAME)
        .applyToParams(List.of(), headerParams, new HashMap<>(), "", "", null);
    if (token != null) {
      assertThat(headerParams, hasEntry(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    } else {
      assertFalse(headerParams.containsKey(HttpHeaders.AUTHORIZATION));
    }
  }
}
