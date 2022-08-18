package bio.terra.catalog.iam;

import bio.terra.catalog.config.SamConfiguration;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.springframework.stereotype.Component;

@Component
public class SamClient {
  private final SamConfiguration samConfig;

  public SamClient(SamConfiguration samConfig) {
    this.samConfig = samConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private ApiClient getApiClient() {
    return new ApiClient().setBasePath(samConfig.basePath());
  }

  UsersApi usersApi(String accessToken) {
    return new UsersApi(getApiClient(accessToken));
  }

  ResourcesApi resourcesApi(String accessToken) {
    return new ResourcesApi(getApiClient(accessToken));
  }

  StatusApi statusApi() {
    return new StatusApi(getApiClient());
  }
}
