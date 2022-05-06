package bio.terra.catalog.rawls;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public record RawlsService(RawlsConfiguration rawlsConfig) {
  private static final Logger logger = LoggerFactory.getLogger(RawlsService.class);

  @Autowired
  public RawlsService {}

  public SystemStatusSystems status() {
    var result = new SystemStatusSystems();
    try {
      // TODO: actually check Rawls status. Does this need the Rawls API client?
      result.ok(true);
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
