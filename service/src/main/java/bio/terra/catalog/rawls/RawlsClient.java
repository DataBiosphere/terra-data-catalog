package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import javax.ws.rs.client.Client;
import org.springframework.stereotype.Component;

@Component
public class RawlsClient {
  private final RawlsConfiguration rawlsConfig;
  private final Client commonHttpClient = new ApiClient().getHttpClient();

  public RawlsClient(RawlsConfiguration rawlsConfig) {
    this.rawlsConfig = rawlsConfig;
  }

  private ApiClient getApiClient(AuthenticatedUserRequest user) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(user.getToken());
    return apiClient;
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(rawlsConfig.basePath());
  }

  WorkspacesApi workspacesApi(AuthenticatedUserRequest user) {
    return new WorkspacesApi(getApiClient(user));
  }

  StatusApi statusApi() {
    return new StatusApi(getApiClient());
  }
}
