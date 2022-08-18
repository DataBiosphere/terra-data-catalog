package bio.terra.catalog.datarepo;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiClient;
import javax.ws.rs.client.Client;
import org.springframework.stereotype.Component;

@Component
public class DatarepoClient {
  private final DatarepoConfiguration datarepoConfig;
  private final Client commonHttpClient = new ApiClient().getHttpClient();

  public DatarepoClient(DatarepoConfiguration datarepoConfig) {
    this.datarepoConfig = datarepoConfig;
  }

  private ApiClient getApiClient(AuthenticatedUserRequest user) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(user.getToken());
    return apiClient;
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(datarepoConfig.basePath());
  }

  SnapshotsApi snapshotsApi(AuthenticatedUserRequest user) {
    return new SnapshotsApi(getApiClient(user));
  }

  UnauthenticatedApi unauthenticatedApi() {
    return new UnauthenticatedApi(getApiClient());
  }
}
