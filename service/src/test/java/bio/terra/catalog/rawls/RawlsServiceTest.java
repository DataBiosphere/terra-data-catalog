package bio.terra.catalog.rawls;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.client.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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

  @Mock private StatusApi statusApi;

  private RawlsService rawlsService;

  @BeforeEach
  void beforeEach() {
    rawlsService = spy(rawlsServiceReal);
    doReturn(statusApi).when(rawlsService).statusApi();
  }

  @Test
  void status() throws Exception {
    var rawlsStatus = rawlsService.status();
    assertTrue(rawlsStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    doThrow(new ApiException()).when(statusApi).systemStatus();
    var rawlsStatus = rawlsService.status();
    assertFalse(rawlsStatus.isOk());
  }
}
