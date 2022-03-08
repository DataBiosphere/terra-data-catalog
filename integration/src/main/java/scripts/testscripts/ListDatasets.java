package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.CatalogClient;

public class ListDatasets extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(ListDatasets.class);

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the list datasets endpoint.");
    var publicApi = new DatasetsApi(new CatalogClient(server, testUser));
    var datasets = publicApi.listDatasets();
    var httpCode = publicApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("Service status return code: {}", httpCode);
    assertFalse(datasets.getResult().isEmpty());
  }
}
