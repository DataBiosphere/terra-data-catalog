package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import bio.terra.catalog.config.StatusCheckConfiguration;
import bio.terra.catalog.model.SystemStatus;
import bio.terra.catalog.model.SystemStatusSystems;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BaseStatusServiceTest {

  @Test
  void getCurrentStatus() {
    var config = new StatusCheckConfiguration(true, 0, 0, 10);
    BaseStatusService service = new BaseStatusService(config);
    var status = new SystemStatusSystems().ok(true);
    service.registerStatusCheck("test", () -> status);
    assertThat(service.getCurrentStatus(), is(new SystemStatus().ok(false)));
    service.checkStatus();
    assertThat(
        service.getCurrentStatus(),
        is(new SystemStatus().ok(true).systems(Map.of("test", status))));
  }

  @Test
  void getCurrentStatusException() {
    var config = new StatusCheckConfiguration(true, 0, 0, 10);
    BaseStatusService service = new BaseStatusService(config);
    var status = new SystemStatusSystems().ok(true);
    service.registerStatusCheck(
        "test",
        () -> {
          throw new RuntimeException("failure");
        });
    SystemStatus systemDown = new SystemStatus().ok(false);
    assertThat(service.getCurrentStatus(), is(systemDown));
    service.checkStatus();
    assertThat(service.getCurrentStatus(), is(systemDown));
  }

  interface Status extends Supplier<SystemStatusSystems> {}

  @Test
  void startStatusChecking() throws InterruptedException {
    var config = new StatusCheckConfiguration(true, 1, 0, 10);
    BaseStatusService service = new BaseStatusService(config);
    var status = mock(Status.class);
    service.registerStatusCheck("", status);
    service.startStatusChecking();
    Thread.sleep(500);
    verify(status).get();
  }

  @Test
  void getNonEnabledStatus() {
    var config = new StatusCheckConfiguration(false, 0, 0, 10);
    BaseStatusService service = new BaseStatusService(config);
    assertThat(service.getCurrentStatus(), is(new SystemStatus().ok(true)));
  }
}