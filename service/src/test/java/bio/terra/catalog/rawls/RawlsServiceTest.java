package bio.terra.catalog.rawls;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.catalog.model.SystemStatusSystems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ConfigurationPropertiesScan("bio.terra.catalog")
@ContextConfiguration(classes = {RawlsService.class})
class RawlsServiceTest {
  @Autowired private RawlsService rawlsServiceReal;

  private RawlsService rawlsService;

  @BeforeEach
  void beforeEach() {
    rawlsService = spy(rawlsServiceReal);
  }

  @Test
  void status() throws Exception {
    SystemStatusSystems status = new SystemStatusSystems().ok(true);
    when(rawlsService.status()).thenReturn(status);
    var rawlsStatus = rawlsService.status();
    assertTrue(rawlsStatus.isOk());
  }

  @Test
  void statusDown() throws Exception {
    SystemStatusSystems status = new SystemStatusSystems().ok(false);
    when(rawlsService.status()).thenReturn(status);
    var rawlsStatus = rawlsService.status();
    assertFalse(rawlsStatus.isOk());
  }

  @Test
  void statusDown2() throws Exception {
    when(rawlsService.statusIsOk()).thenReturn(false);
    var rawlsStatus = rawlsService.status();
    assertFalse(rawlsStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    when(rawlsService.statusIsOk()).thenThrow(new Exception());
    var rawlsStatus = rawlsService.status();
    assertFalse(rawlsStatus.isOk());
  }
}
