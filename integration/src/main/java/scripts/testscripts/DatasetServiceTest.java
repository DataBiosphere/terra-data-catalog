package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.catalog.api.DatasetsApi;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.CatalogClient;
import scripts.client.DataRepoClient;

public class DatasetServiceTest extends TestScript {

  private static final Logger log = LoggerFactory.getLogger(DatasetServiceTest.class);

  //  private SnapshotModel

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    var apiClient = new DataRepoClient(server, testUsers.get(0));
    var datasetsApi =
        new bio.terra.datarepo.api.DatasetsApi(apiClient);
    var dataset =
        datasetsApi.enumerateDatasets(null, null, null, null, "CatalogTestDataset", null).getItems().get(0);

    var snapshotApi = new SnapshotsApi(apiClient)
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {}

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    // given a snapshot, create a catalog entry
    // retrieve the entry
    // modify the entry, verify
    // delete the entry, verify
    log.info("Checking the list datasets endpoint.");
    var publicApi = new DatasetsApi(new CatalogClient(server, testUser));
    var datasets = publicApi.listDatasets();
    var httpCode = publicApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("Service status return code: {}", httpCode);
    assertFalse(datasets.getResult().isEmpty());
  }
}
