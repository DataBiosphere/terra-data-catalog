package bio.terra.catalog.datarepo;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.catalog.iam.User;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiClient;
import javax.ws.rs.client.Client;
import org.springframework.stereotype.Component;

@Component
public class DatarepoClient {
  private final DatarepoConfiguration datarepoConfig;
  private final User user;
  private final Client commonHttpClient = new ApiClient().getHttpClient();

  public DatarepoClient(DatarepoConfiguration datarepoConfig, User user) {
    this.datarepoConfig = datarepoConfig;
    this.user = user;
  }

  private ApiClient getAuthApiClient() {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(user.getToken());
    return apiClient;
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(datarepoConfig.basePath());
  }

  SnapshotsApi snapshotsApi() {
    return new SnapshotsApi(getAuthApiClient());
  }

  UnauthenticatedApi unauthenticatedApi() {
    return new UnauthenticatedApi(getApiClient());
  }
}
