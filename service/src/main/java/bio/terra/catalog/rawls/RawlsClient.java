package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.EntitiesApi;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiClient;
import com.google.common.annotations.VisibleForTesting;
import javax.ws.rs.core.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class RawlsClient {
  private final RawlsConfiguration rawlsConfig;

  public RawlsClient(RawlsConfiguration rawlsConfig) {
    this.rawlsConfig = rawlsConfig;
  }

  @VisibleForTesting
  ApiClient getApiClient(AuthenticatedUserRequest user) {
    ApiClient apiClient = getApiClient();
    apiClient.setRequestInterceptor(
        request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getToken()));
    return apiClient;
  }

  @VisibleForTesting
  ApiClient getApiClient() {
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
