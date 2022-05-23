package scripts.api;

import bio.terra.datarepo.model.ColumnModel;
import bio.terra.datarepo.model.DatasetModel;
import bio.terra.datarepo.model.DatasetRequestModel;
import bio.terra.datarepo.model.DatasetSpecificationModel;
import bio.terra.datarepo.model.IngestRequestModel;
import bio.terra.datarepo.model.PolicyMemberRequest;
import bio.terra.datarepo.model.TableDataType;
import bio.terra.datarepo.model.TableModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.DatarepoClient;

public class TdrDatasetsApi {
  private static final Logger log = LoggerFactory.getLogger(TdrDatasetsApi.class);

  private final bio.terra.datarepo.api.DatasetsApi datasetsApi;

  public TdrDatasetsApi(DatarepoClient apiClient) {
    datasetsApi = new bio.terra.datarepo.api.DatasetsApi(apiClient);
  }

  private DatarepoClient getApiClient() {
    return (DatarepoClient) datasetsApi.getApiClient();
  }

  private DatasetRequestModel createDatasetRequestModel() {
    var schema =
        new DatasetSpecificationModel()
            .tables(
                List.of(
                    new TableModel()
                        .name("participant")
                        .columns(
                            List.of(
                                new ColumnModel()
                                    .name("participant_id")
                                    .datatype(TableDataType.STRING),
                                new ColumnModel()
                                    .name("biological_sex")
                                    .datatype(TableDataType.STRING),
                                new ColumnModel().name("age").datatype(TableDataType.INTEGER))),
                    new TableModel()
                        .name("sample")
                        .columns(
                            List.of(
                                new ColumnModel().name("sample_id").datatype(TableDataType.STRING),
                                new ColumnModel()
                                    .name("participant_id")
                                    .datatype(TableDataType.STRING),
                                new ColumnModel()
                                    .name("files")
                                    .datatype(TableDataType.STRING)
                                    .arrayOf(true),
                                new ColumnModel().name("type").datatype(TableDataType.STRING)))));
    return new DatasetRequestModel()
        .name("catalog_integration_test_" + System.currentTimeMillis())
        .defaultProfileId(UUID.fromString("1d29ed8f-6554-4366-8fa0-e212ee553d29"))
        .schema(schema);
  }

  public DatasetModel createTestDataset() throws Exception {
    var request = createDatasetRequestModel();
    String createJobId = datasetsApi.createDataset(request).getId();
    var result = getApiClient().waitForJob(createJobId);
    UUID id = UUID.fromString(result.get("id"));
    log.info("created TDR dataset {} - {}", id, request.getName());
    var dataset = datasetsApi.retrieveDataset(id, List.of());
    ingestData(dataset);
    log.info("data ingest complete");
    return dataset;
  }

  public void ingestData(DatasetModel dataset) throws Exception {
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

  public void deleteDataset(UUID id) throws Exception {
    getApiClient().waitForJob(datasetsApi.deleteDataset(id).getId());
    log.info("deleted dataset " + id);
  }

  public void addDatasetPolicyMember(UUID id, String policyName, String email) throws Exception {
    datasetsApi.addDatasetPolicyMember(id, policyName, new PolicyMemberRequest().email(email));
  }
}
