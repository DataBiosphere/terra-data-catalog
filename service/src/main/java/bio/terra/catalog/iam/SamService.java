package bio.terra.catalog.iam;

import bio.terra.catalog.config.SamConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.common.sam.SamRetry;
import bio.terra.common.sam.exception.SamExceptionFactory;
import java.util.List;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamService {
  private static final Logger logger = LoggerFactory.getLogger(SamService.class);
  private final SamConfiguration samConfig;
  private final SamClient samClient;

  private final SamAuthenticatedUserRequestFactory requestFactory;

  private static final String CATALOG_RESOURCE_TYPE = "catalog";

  @Autowired
  public SamService(
      SamConfiguration samConfig,
      SamClient samClient,
      SamAuthenticatedUserRequestFactory requestFactory) {
    this.samConfig = samConfig;
    this.samClient = samClient;
    this.requestFactory = requestFactory;
  }

  /**
   * Checks if a user has an action on all catalog resources.
   *
   * <p>This checks the action against the "global" catalog resource, which is used for global
   * permission checks.
   *
   * @param action sam action
   * @return true if the user has any actions on that resource; false otherwise.
   */
  public boolean hasGlobalAction(SamAction action) {
    String accessToken = requestFactory.getUser().getToken();
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

  public SystemStatusSystems status() {
    // No access token needed since this is an unauthenticated API.
    try {
      // Don't retry status check
      SystemStatus samStatus = samClient.statusApi().getSystemStatus();
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
}
