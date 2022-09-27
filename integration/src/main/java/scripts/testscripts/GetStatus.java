package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import bio.terra.catalog.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import scripts.client.CatalogClient;
import java.util.concurrent.atomic.AtomicInteger;

public class GetStatus extends TestScript {
  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    var client = new CatalogClient(server);
    var publicApi = new PublicApi(client);
    AtomicInteger status = new AtomicInteger();
    client.setResponseInterceptor(response -> status.set(response.statusCode()));
    publicApi.getStatus();
    assertThat(status.get(), is(HttpStatusCodes.STATUS_CODE_OK));
  }
}
