package scripts.api;

import bio.terra.datarepo.api.ProfilesApi;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.ColumnModel;
import bio.terra.datarepo.model.DatasetModel;
import bio.terra.datarepo.model.DatasetRequestModel;
import bio.terra.datarepo.model.DatasetSpecificationModel;
import bio.terra.datarepo.model.IngestRequestModel;
import bio.terra.datarepo.model.PolicyMemberRequest;
import bio.terra.datarepo.model.TableDataType;
import bio.terra.datarepo.model.TableModel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.DatarepoClient;

public class TdrDatasetsApi {
  private static final Logger log = LoggerFactory.getLogger(TdrDatasetsApi.class);
  private static final String TEST_BILLING_PROFILE_PREFIX = "catalog_test_profile";
  private static TdrDatasetsApi theApi;
  private DatasetModel testDataset;

  private final bio.terra.datarepo.api.DatasetsApi datasetsApi;

  private TdrDatasetsApi(DatarepoClient apiClient) {
    datasetsApi = new bio.terra.datarepo.api.DatasetsApi(apiClient);
    // Register a shutdown hook to clean up the test dataset on JVM shutdown. It's done this way
    // because we don't know which test that uses a dataset will run last, and we don't want to
    // make and delete a dataset for every test.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (testDataset != null) {
                    try {
                      deleteDataset(testDataset.getId());
                      testDataset = null;
                    } catch (Exception e) {
                      log.error("error deleting dataset during shutdown", e);
                    }
                  }
                }));
  }

  public static synchronized TdrDatasetsApi createApi(DatarepoClient apiClient) {
    if (theApi == null) {
      theApi = new TdrDatasetsApi(apiClient);
    }
    return theApi;
  }

  private DatarepoClient getApiClient() {
    return (DatarepoClient) datasetsApi.getApiClient();
  }

  private UUID getBillingProfileId() throws ApiException {
    ProfilesApi profilesApi = new ProfilesApi(getApiClient());
    return profilesApi.enumerateProfiles(0, 100).getItems().stream()
        .filter(model -> model.getProfileName().startsWith(TEST_BILLING_PROFILE_PREFIX))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Test billing profile not found"))
        .getId();
  }

  private static class Column extends ColumnModel {
    Column(String name, TableDataType dataType) {
      setName(name);
      setDatatype(dataType);
    }
  }

  private static class Table extends TableModel {
    Table(String name, ColumnModel... columns) {
      setName(name);
      setColumns(Arrays.asList(columns));
    }
  }

  private DatasetRequestModel createDatasetRequestModel(UUID billingProfileId) {
    var schema =
        new DatasetSpecificationModel()
            .tables(
                List.of(
                    new Table(
                        "participant",
                        new Column("participant_id", TableDataType.STRING),
                        new Column("biological_sex", TableDataType.STRING),
                        new Column("age", TableDataType.INTEGER)),
                    new Table(
                        "sample",
                        new Column("sample_id", TableDataType.STRING),
                        new Column("participant_id", TableDataType.STRING),
                        new Column("files", TableDataType.STRING).arrayOf(true),
                        new Column("type", TableDataType.STRING))));
    return new DatasetRequestModel()
        .name("catalog_integration_test_" + System.currentTimeMillis())
        .defaultProfileId(billingProfileId)
        .schema(schema);
  }

  /**
   * Return the test dataset for integration tests. If the dataset doesn't exist yet, it's created
   * and stored, so it will be deleted on JVM shutdown.
   *
   * @return the test dataset
   */
  public synchronized DatasetModel getTestDataset() throws Exception {
    if (testDataset == null) {
      var billingProfileId = getBillingProfileId();
      var request = createDatasetRequestModel(billingProfileId);
      String createJobId = datasetsApi.createDataset(request).getId();
      var result = getApiClient().waitForJob(createJobId);
      UUID id = UUID.fromString(result.get("id"));
      log.info("created TDR dataset {} - {}", id, request.getName());
      testDataset = datasetsApi.retrieveDataset(id, List.of());
      ingestData(testDataset);
      log.info("data ingest complete");
    }
    return testDataset;
  }

  private void ingestData(DatasetModel dataset) throws Exception {
    var participants =
        new IngestRequestModel().table("participant").format(IngestRequestModel.FormatEnum.ARRAY);
    for (int i = 1; i <= 15; i++) {
      participants.addRecordsItem(
          Map.of(
              "age",
              i + 10,
              "biological_sex",
              i % 2 == 0 ? "male" : "female",
              "participant_id",
              "participant" + i));
    }
    getApiClient().waitForJob(datasetsApi.ingestDataset(dataset.getId(), participants).getId());

    var samples =
        new IngestRequestModel().table("sample").format(IngestRequestModel.FormatEnum.ARRAY);
    for (int i = 1; i <= 15; i++) {
      samples.addRecordsItem(
          Map.of(
              "files",
              List.of(1, 2, 3, 4, 5),
              "sample_id",
              "sample" + i,
              "type",
              "bam",
              "participant_id",
              "participant" + i));
    }
    getApiClient().waitForJob(datasetsApi.ingestDataset(dataset.getId(), samples).getId());
  }

  private void deleteDataset(UUID id) throws Exception {
    getApiClient().waitForJob(datasetsApi.deleteDataset(id).getId());
    log.info("deleted dataset " + id);
  }

  public void addDatasetPolicyMember(UUID id, String policyName, String email) throws Exception {
    datasetsApi.addDatasetPolicyMember(id, policyName, new PolicyMemberRequest().email(email));
  }
}
