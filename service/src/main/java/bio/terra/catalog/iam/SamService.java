package bio.terra.catalog.iam;

import bio.terra.catalog.config.SamConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.common.sam.SamRetry;
import bio.terra.common.sam.exception.SamExceptionFactory;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import okhttp3.OkHttpClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamService {
  private static final Logger logger = LoggerFactory.getLogger(SamService.class);
  private final SamConfiguration samConfig;
  private final OkHttpClient commonHttpClient;

  private static final String CATALOG_RESOURCE_TYPE = "catalog";

  @Autowired
  public SamService(SamConfiguration samConfig) {
    this.samConfig = samConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  /**
   * Checks if a user has an action on all catalog resources.
   *
   * <p>This checks the action against the "global" catalog resource, which is used for global
   * permission checks.
   *
   * @param userRequest authenticated user
   * @param action sam action
   * @return true if the user has any actions on that resource; false otherwise.
   */
  public boolean hasGlobalAction(AuthenticatedUserRequest userRequest, SamAction action) {
    String accessToken = userRequest.getToken();
    ResourcesApi resourceApi = resourcesApi(accessToken);
    try {
      return SamRetry.retry(
              () -> resourceApi.resourceActions(CATALOG_RESOURCE_TYPE, samConfig.resourceId()))
          .stream()
          .map(SamAction::fromValue)
          .anyMatch(action::equals);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error checking resource permission in Sam", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw SamExceptionFactory.create("Error checking resource permission in Sam", e);
    }
  }

  /**
   * Fetch the user status (email and subjectId) from Sam.
   *
   * @param userToken user token
   * @return {@link UserStatusInfo}
   */
  public UserStatusInfo getUserStatusInfo(String userToken) {
    UsersApi usersApi = usersApi(userToken);
    try {
      return SamRetry.retry(usersApi::getUserStatusInfo);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error getting user email from Sam", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw SamExceptionFactory.create("Error getting user email from Sam", e);
    }
  }

  public SystemStatusSystems status() {
    // No access token needed since this is an unauthenticated API.
    try {
      // Don't retry status check
      SystemStatus samStatus = statusApi().getSystemStatus();
      var result = new SystemStatusSystems().ok(samStatus.getOk());
      var samSystems = samStatus.getSystems();
      // Populate error message if Sam status is non-ok
      if (!samStatus.getOk()) {
        String errorMsg = "Sam status check failed. Messages = " + samSystems;
        logger.error(errorMsg);
        result.addMessagesItem(errorMsg);
      }
      return result;
    } catch (Exception e) {
      String errorMsg = "Sam status check failed";
      logger.error(errorMsg, e);
      return new SystemStatusSystems().ok(false).messages(List.of(errorMsg));
    }
  }

  @VisibleForTesting
  UsersApi usersApi(String accessToken) {
    return new UsersApi(getApiClient(accessToken));
  }

  @VisibleForTesting
  ResourcesApi resourcesApi(String accessToken) {
    return new ResourcesApi(getApiClient(accessToken));
  }

  @VisibleForTesting
  StatusApi statusApi() {
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
