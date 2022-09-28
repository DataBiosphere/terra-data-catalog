package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import java.net.http.HttpRequest;
import javax.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;

class RawlsClientTest {

  private static final String BASE_PATH = "http://some.host/path";
  private static final String TOKEN = "token";

  private final RawlsClient client = new RawlsClient(new RawlsConfiguration(BASE_PATH));

  @Test
  void testApis() {
    var user =
        new AuthenticatedUserRequest.Builder()
            .setEmail("")
            .setSubjectId("")
            .setToken(TOKEN)
            .build();

    var apiClient = client.getApiClient(user);
    assertThat(apiClient.getBaseUri(), is(BASE_PATH));
    var requestBuilder = mock(HttpRequest.Builder.class);
    apiClient.getRequestInterceptor().accept(requestBuilder);
    verify(requestBuilder).header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getToken());

    apiClient = client.getApiClient();
    assertNull(apiClient.getRequestInterceptor());
  }
}
