package bio.terra.catalog.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.catalog.config.SamConfiguration;
import org.broadinstitute.dsde.workbench.client.sam.auth.OAuth;
import org.junit.jupiter.api.Test;

class SamClientTest {
  private static final String BASE_PATH = "basepath";
  private static final String TOKEN = "token";

  private final SamClient client;

  SamClientTest() {
    client = new SamClient(new SamConfiguration(BASE_PATH, "resourceId"));
  }

  @Test
  void testApis() {
    var usersClient = client.usersApi(TOKEN).getApiClient();
    assertThat(usersClient.getBasePath(), is(BASE_PATH));

    OAuth oauth = (OAuth) usersClient.getAuthentication("googleoauth");
    assertNotNull(oauth);
    assertThat(oauth.getAccessToken(), is(TOKEN));

    var statusClient = client.statusApi().getApiClient();
    assertThat(statusClient.getBasePath(), is(BASE_PATH));

    var resourcesClient = client.resourcesApi(TOKEN).getApiClient();
    assertThat(resourcesClient.getBasePath(), is(BASE_PATH));
  }
}
