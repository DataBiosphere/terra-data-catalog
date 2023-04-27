package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.StatusCheckConfiguration;
import bio.terra.catalog.model.SystemStatus;
import bio.terra.catalog.model.SystemStatusSystems;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class StatusCheckServiceTest {

  @Test
  void getCurrentStatus() {
    var config = new StatusCheckConfiguration(true, 0, 0, 10);
    StatusCheckService service = new StatusCheckService(config);
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
    StatusCheckService service = new StatusCheckService(config);
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
  void startStatusChecking() {
    var config = new StatusCheckConfiguration(true, 1, 0, 10);
    StatusCheckService service = new StatusCheckService(config);
    var status = mock(Status.class);
    when(status.get()).thenReturn(new SystemStatusSystems().ok(true));
    service.registerStatusCheck("", status);
    service.startStatusChecking();
    verify(status, timeout(500)).get();
  }

  @Test
  void getNonEnabledStatus() {
    var config = new StatusCheckConfiguration(false, 0, 0, 10);
    StatusCheckService service = new StatusCheckService(config);
    assertThat(service.getCurrentStatus(), is(new SystemStatus().ok(true)));
    var status = mock(Status.class);
    service.registerStatusCheck("", status);
    service.startStatusChecking();
    service.checkStatus();
    verifyNoInteractions(status);
  }

  @Test
  void getCurrentStatusStale() {
    var config = new StatusCheckConfiguration(true, 0, 0, 0);
    StatusCheckService service = new StatusCheckService(config);
    assertThat(service.getCurrentStatus(), is(new SystemStatus().ok(false)));
  }
}
