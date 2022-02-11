package scripts.testscripts;

import bio.terra.catalog.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scripts.utils.ClientTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetStatus extends TestScript {

  private static final Logger log = LogManager.getLogger(GetStatus.class);

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the service status endpoint.");
    var apiClient = ClientTestUtils.getClientWithoutAuth(server);
    var publicApi = new PublicApi(apiClient);
    publicApi.getStatus();
    var httpCode = publicApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("Service status return code: {}", httpCode);
  }
}
