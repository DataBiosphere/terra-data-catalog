package scripts.client;

import bio.terra.datarepo.api.JobsApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.JobModel;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import scripts.api.TdrDatasetsApi;

public class DatarepoClient extends ApiClient {

  /**
   * Generate a random name for use in TDR APIs. TDR doesn't allow hyphens so they're converted to
   * underscores.
   *
   * @return the random name.
   */
  public static String randomName() {
    return "catalog_integration_test_" + UUID.randomUUID().toString().replace('-', '_');
  }

  /**
   * Build the API client object for the given test user and datarepo server. The test user's token
   * is always refreshed. If a test user isn't configured (e.g. when running locally), return an
   * un-authenticated client.
   *
   * @param server the server we are testing against
   * @param testUser the test user whose credentials are supplied to the API client object
   */
  public DatarepoClient(ServerSpecification server, TestUserSpecification testUser)
      throws IOException {
    setBasePath(Objects.requireNonNull(server.datarepoUri, "Datarepo URI required"));

    if (testUser != null) {
      GoogleCredentials userCredential =
          AuthenticationUtils.getDelegatedUserCredential(
              testUser, AuthenticationUtils.userLoginScopes);
      var accessToken = AuthenticationUtils.getAccessToken(userCredential);
      if (accessToken != null) {
        setAccessToken(accessToken.getTokenValue());
      }
    }
  }

  public TdrDatasetsApi datasetsApi() {
    return TdrDatasetsApi.createApi(this);
  }

  public Map<Object, String> waitForJob(String jobId) throws ApiException, InterruptedException {
    JobModel job;
    var jobsApi = new JobsApi(this);
    do {
      job = jobsApi.retrieveJob(jobId);
      TimeUnit.SECONDS.sleep(5);
    } while (job.getJobStatus() == JobModel.JobStatusEnum.RUNNING);

    if (job.getJobStatus() != JobModel.JobStatusEnum.SUCCEEDED) {
      throw new RuntimeException("Job failed: " + job);
    }

    @SuppressWarnings("unchecked")
    var result = (Map<Object, String>) jobsApi.retrieveJobResult(jobId);
    return result;
  }
}
