package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.catalog.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.CatalogClient;

public class GetStatus extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(GetStatus.class);

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the service status endpoint.");
    var publicApi = new PublicApi(new CatalogClient(server));
    publicApi.getStatus();
    var httpCode = publicApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("Service status return code: {}", httpCode);
  }
}
