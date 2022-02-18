package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.catalog.api.DatasetApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;

public class ListDatasets extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(ListDatasets.class);

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the list datasets endpoint.");
    var apiClient = ClientTestUtils.getClientWithTestUserAuth(testUser, server);
    var publicApi = new DatasetApi(apiClient);
    publicApi.listDatasets();
    var httpCode = publicApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("Service status return code: {}", httpCode);
  }
}
