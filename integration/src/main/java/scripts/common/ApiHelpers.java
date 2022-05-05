package scripts.common;

import bio.terra.datarepo.api.JobsApi;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.JobModel;
import bio.terra.datarepo.model.SnapshotRequestModel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ApiHelpers {
  public static Map<Object, Object> waitForJobSuccess(ApiClient datarepoClient, String jobId)
      throws ApiException, InterruptedException {
    var jobApi = new JobsApi(datarepoClient);
    JobModel job;
    do {
      job = jobApi.retrieveJob(jobId);
      TimeUnit.SECONDS.sleep(5);
    } while (job.getJobStatus() == JobModel.JobStatusEnum.RUNNING);

    if (job.getJobStatus() != JobModel.JobStatusEnum.SUCCEEDED) {
      throw new RuntimeException("Job failed: " + job);
    }
    return (Map<Object, Object>) jobApi.retrieveJobResult(jobId);
  }

  public static UUID synchronousCreateSnapshot(
      SnapshotsApi snapshotsApi, SnapshotRequestModel request)
      throws ApiException, InterruptedException {
    String createJobId = snapshotsApi.createSnapshot(request).getId();
    Map<Object, Object> result = waitForJobSuccess(snapshotsApi.getApiClient(), createJobId);
    UUID snapshotId = UUID.fromString((String) result.get("id"));
    return snapshotId;
  }

  public static void synchronousDeleteSnapshot(SnapshotsApi snapshotsApi, UUID snapshotId)
      throws ApiException, InterruptedException {
    String deleteJobId = snapshotsApi.deleteSnapshot(snapshotId).getId();
    waitForJobSuccess(snapshotsApi.getApiClient(), deleteJobId);
  }
}
