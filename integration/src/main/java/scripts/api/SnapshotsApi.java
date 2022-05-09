package scripts.api;

import bio.terra.datarepo.model.SnapshotRequestModel;
import scripts.client.DatarepoClient;
import java.util.Map;
import java.util.UUID;

public class SnapshotsApi extends bio.terra.datarepo.api.SnapshotsApi {

  public SnapshotsApi(DatarepoClient apiClient) {
    super(apiClient);
  }

  public DatarepoClient getApiClient() {
    return (DatarepoClient) super.getApiClient();
  }

  public UUID synchronousCreateSnapshot(SnapshotRequestModel request) throws Exception {
    String createJobId = createSnapshot(request).getId();
    Map<Object, Object> result = getApiClient().waitForJobSuccess(createJobId);
    UUID snapshotId = UUID.fromString((String) result.get("id"));
    return snapshotId;
  }

  public void synchronousDeleteSnapshot(UUID snapshotId) throws Exception {
    String deleteJobId = deleteSnapshot(snapshotId).getId();
    getApiClient().waitForJobSuccess(deleteJobId);
  }
}
