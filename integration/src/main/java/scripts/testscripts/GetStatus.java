package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.catalog.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import scripts.client.CatalogClient;

public class GetStatus extends TestScript {
  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    var client = new CatalogClient(server);
    var publicApi = new PublicApi(client);
    var response = publicApi.getStatusWithHttpInfo();
    assertThat(response.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));
    assertTrue(response.getData().getOk());
  }
}
