package scripts.testscripts;

import bio.terra.catalog.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GetVersion extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(GetStatus.class);

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the version endpoint.");
    var apiClient = ClientTestUtils.getClientWithoutAuth(server);
    var publicApi = new PublicApi(apiClient);

    var versionProperties = publicApi.getVersion();

    // check the response code
    var httpCode = publicApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("Service status return code: {}", httpCode);

    // check the response body
    assertNotNull(versionProperties.getGitHash());
    assertNotNull(versionProperties.getGitTag());
    assertNotNull(versionProperties.getGithub());
    assertNotNull(versionProperties.getBuild());
  }
}
