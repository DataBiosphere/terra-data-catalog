package scripts.api;

import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.DatasetModel;
import bio.terra.datarepo.model.PolicyMemberRequest;
import bio.terra.datarepo.model.SnapshotRequestContentsModel;
import bio.terra.datarepo.model.SnapshotRequestModel;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.client.DatarepoClient;

public class SnapshotsApi {
  private static final Logger log = LoggerFactory.getLogger(SnapshotsApi.class);

  private final bio.terra.datarepo.api.SnapshotsApi snapshotsApi;

  public SnapshotsApi(DatarepoClient apiClient) {
    snapshotsApi = new bio.terra.datarepo.api.SnapshotsApi(apiClient);
  }

  private DatarepoClient getApiClient() {
    return (DatarepoClient) snapshotsApi.getApiClient();
  }

  private static SnapshotRequestModel createRequest(DatasetModel dataset) {
    return new SnapshotRequestModel()
        .name(DatarepoClient.randomName())
        .description("test snapshot")
        .profileId(dataset.getDefaultProfileId())
        .addContentsItem(
            new SnapshotRequestContentsModel()
                .datasetName(dataset.getName())
                .mode(SnapshotRequestContentsModel.ModeEnum.BYFULLVIEW));
  }

  public UUID createTestSnapshot(DatasetModel dataset) throws Exception {
    var request = createRequest(dataset);
    String createJobId = snapshotsApi.createSnapshot(request).getId();
    var result = getApiClient().waitForJob(createJobId);
    UUID id = UUID.fromString(result.get("id"));
    log.info("created snapshot {} - {}", id, request.getName());
    return id;
  }

  public void delete(UUID snapshotId) throws Exception {
    getApiClient().waitForJob(snapshotsApi.deleteSnapshot(snapshotId).getId());
    log.info("deleted snapshot {}", snapshotId);
  }

  public void addPolicyMember(UUID id, String policy, String email) throws ApiException {
    snapshotsApi.addSnapshotPolicyMember(id, policy, new PolicyMemberRequest().email(email));
  }

  public void deletePolicyMember(UUID id, String policy, String email) throws ApiException {
    snapshotsApi.deleteSnapshotPolicyMember(id, policy, email);
  }
}
