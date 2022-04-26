package bio.terra.catalog.service;

import bio.terra.catalog.config.StatusCheckConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseStatusServiceTest {

  @Test
  void getCurrentStatus() {
    var config = new StatusCheckConfiguration(true, 2, 0, 3);
    BaseStatusService service = new BaseStatusService(config);
    var status = new SystemStatusSystems().ok(false).addMessagesItem("down");
    service.registerStatusCheck("test", () -> status);
  }
}
