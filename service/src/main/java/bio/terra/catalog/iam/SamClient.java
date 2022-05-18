package bio.terra.catalog.iam;

import bio.terra.catalog.config.SamConfiguration;
import okhttp3.OkHttpClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamClient {
  private final SamConfiguration samConfig;
  private final OkHttpClient commonHttpClient;

  @Autowired
  public SamClient(SamConfiguration samConfig) {
    this.samConfig = samConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  public UsersApi usersApi(String accessToken) {
    return new UsersApi(getApiClient(accessToken));
  }

  public ResourcesApi resourcesApi(String accessToken) {
    return new ResourcesApi(getApiClient(accessToken));
  }

  public StatusApi statusApi() {
    return new StatusApi(getApiClient(null));
  }

  private ApiClient getApiClient(String accessToken) {
    // OkHttpClient objects manage their own thread pools, so it's much more performant to share one
    // across requests.
    ApiClient apiClient =
        new ApiClient().setHttpClient(commonHttpClient).setBasePath(samConfig.basePath());
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }
}
