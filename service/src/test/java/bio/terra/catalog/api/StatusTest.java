package bio.terra.catalog.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.catalog.config.VersionConfiguration;
import bio.terra.catalog.model.SystemStatus;
import bio.terra.catalog.service.CatalogStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PublicApiController.class)
@WebMvcTest
public class StatusTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private CatalogStatusService statusService;

  @MockBean private VersionConfiguration versionConfiguration;

  @Test
  public void testStatus() throws Exception {
    SystemStatus systemStatus = new SystemStatus().ok(true);
    when(statusService.getCurrentStatus()).thenReturn(systemStatus);
    this.mockMvc.perform(get("/status")).andExpect(status().isOk());
  }

  @Test
  public void testStatusCheckFails() throws Exception {
    SystemStatus systemStatus = new SystemStatus().ok(false);
    when(statusService.getCurrentStatus()).thenReturn(systemStatus);
    this.mockMvc.perform(get("/status")).andExpect(status().is5xxServerError());
  }
}
