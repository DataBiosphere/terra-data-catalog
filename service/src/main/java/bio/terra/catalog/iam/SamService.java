package bio.terra.catalog.iam;

import bio.terra.catalog.config.SamConfiguration;
import bio.terra.catalog.model.SystemStatusSystemsValue;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.common.sam.SamRetry;
import bio.terra.common.sam.exception.SamExceptionFactory;
import java.util.List;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
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
  private final SamClient samClient;

  private static final String CATALOG_RESOURCE_TYPE = "catalog";

  @Autowired
  public SamService(SamConfiguration samConfig, SamClient samClient) {
    this.samConfig = samConfig;
    this.samClient = samClient;
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
    ResourcesApi resourceApi = samClient.resourcesApi(accessToken);
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
    UsersApi usersApi = samClient.usersApi(userToken);
    try {
      return SamRetry.retry(usersApi::getUserStatusInfo);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error getting user email from Sam", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw SamExceptionFactory.create("Error getting user email from Sam", e);
    }
  }

  public SystemStatusSystemsValue status() {
    // No access token needed since this is an unauthenticated API.
    try {
      // Don't retry status check
      SystemStatus samStatus = samClient.statusApi().getSystemStatus();
      var result = new SystemStatusSystemsValue().ok(samStatus.getOk());
      var samSystems = samStatus.getSystems();
      // Populate error message if Sam status is non-ok
      if (result.getOk() == null || !result.getOk()) {
        String errorMsg = "Sam status check failed. Messages = " + samSystems;
        logger.error(errorMsg);
        result.addMessagesItem(errorMsg);
      }
      return result;
    } catch (Exception e) {
      String errorMsg = "Sam status check failed";
      logger.error(errorMsg, e);
      return new SystemStatusSystemsValue().ok(false).messages(List.of(errorMsg));
    }
  }
}
