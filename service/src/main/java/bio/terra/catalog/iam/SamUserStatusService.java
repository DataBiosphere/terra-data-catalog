package bio.terra.catalog.iam;

import bio.terra.catalog.config.SamConfiguration;
import bio.terra.common.sam.SamRetry;
import bio.terra.common.sam.exception.SamExceptionFactory;
import com.google.common.annotations.VisibleForTesting;
import okhttp3.OkHttpClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamUserStatusService {
  private final SamConfiguration samConfig;
  private final OkHttpClient commonHttpClient;

  @Autowired
  public SamUserStatusService(SamConfiguration samConfig) {
    this.samConfig = samConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  /**
   * Fetch the user status (email and subjectId) from Sam.
   *
   * @param userToken user token
   * @return {@link UserStatusInfo}
   */
  public UserStatusInfo getUserStatusInfo(String userToken) {
    UsersApi usersApi = samUsersApi(userToken);
    try {
      return SamRetry.retry(usersApi::getUserStatusInfo);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error getting user email from Sam", e);
    } catch (InterruptedException e) {
      throw SamExceptionFactory.create("Error getting user email from Sam", e);
    }
  }

  @VisibleForTesting
  UsersApi samUsersApi(String accessToken) {
    return new UsersApi(getApiClient(accessToken));
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
