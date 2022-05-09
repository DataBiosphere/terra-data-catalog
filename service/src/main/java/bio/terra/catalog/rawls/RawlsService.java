package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.client.ApiClient;
import bio.terra.rawls.client.ApiException;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RawlsService {
  private static final Logger logger = LoggerFactory.getLogger(RawlsService.class);

  private final RawlsConfiguration rawlsConfig;
  private final Client commonHttpClient;

  @Autowired
  public RawlsService(RawlsConfiguration rawlsConfig) {
    this.rawlsConfig = rawlsConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  private ApiClient getApiClient() {
    // Share one api client across requests.
    return new ApiClient().setHttpClient(commonHttpClient).setBasePath(rawlsConfig.basePath());
  }

  public boolean statusIsOk() throws ApiException {
    new StatusApi(getApiClient()).systemStatus();
    return true;
  }

  public SystemStatusSystems status() {
    var result = new SystemStatusSystems();
    try {
      // If the status is down then this method will throw
      var status = statusIsOk();
      result.ok(status);
      if (!result.isOk()) {
        String errorMsg = "Rawls status check failed.";
        logger.error(errorMsg);
        result.addMessagesItem(errorMsg);
      }
    } catch (Exception e) {
      String errorMsg = "Rawls status check failed";
      logger.error(errorMsg, e);
      result.ok(false).addMessagesItem(errorMsg);
    }
    return result;
  }
}
