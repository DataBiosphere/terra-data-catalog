package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.EntitiesApi;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import org.springframework.stereotype.Component;

@Component
public class RawlsClient {
  private final RawlsConfiguration rawlsConfig;

  public RawlsClient(RawlsConfiguration rawlsConfig) {
    this.rawlsConfig = rawlsConfig;
  }

  private ApiClient getApiClient(AuthenticatedUserRequest user) {
    ApiClient apiClient = getApiClient();
    apiClient.setRequestInterceptor(builder -> builder.header("Authorization", "Bearer " + user.getToken()));
    return apiClient;
  }

  private ApiClient getApiClient() {
    var apiClient = new ApiClient();
    apiClient.updateBaseUri(rawlsConfig.basePath());
    return apiClient;
  }

  WorkspacesApi workspacesApi(AuthenticatedUserRequest user) {
    return new WorkspacesApi(getApiClient(user));
  }

  EntitiesApi entitiesApi(AuthenticatedUserRequest user) {
    return new EntitiesApi(getApiClient(user));
  }

  StatusApi statusApi() {
    return new StatusApi(getApiClient());
  }
}
