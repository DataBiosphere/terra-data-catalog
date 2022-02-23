package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.catalog.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scripts.utils.CatalogClient;

public class GetVersion extends TestScript {

  private static final Logger log = LogManager.getLogger(GetStatus.class);

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the version endpoint.");
    var publicApi = new PublicApi(new CatalogClient(server));

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
