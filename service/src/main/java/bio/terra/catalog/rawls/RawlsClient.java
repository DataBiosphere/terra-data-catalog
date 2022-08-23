package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.rawls.api.EntitiesApi;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import javax.ws.rs.client.Client;
import org.springframework.stereotype.Component;

@Component
public class RawlsClient {
  private final RawlsConfiguration rawlsConfig;
  private final SamAuthenticatedUserRequestFactory userFactory;
  private final Client commonHttpClient = new ApiClient().getHttpClient();

  public RawlsClient(
      RawlsConfiguration rawlsConfig, SamAuthenticatedUserRequestFactory userFactory) {
    this.rawlsConfig = rawlsConfig;
    this.userFactory = userFactory;
  }

  private ApiClient getAuthApiClient() {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(userFactory.getUser().getToken());
    return apiClient;
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(rawlsConfig.basePath());
  }

  WorkspacesApi workspacesApi() {
    return new WorkspacesApi(getAuthApiClient());
  }

  EntitiesApi entitiesApi() {
    return new EntitiesApi(getAuthApiClient());
  }

  StatusApi statusApi() {
    return new StatusApi(getApiClient());
  }
}
