package bio.terra.catalog.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.catalog.config.VersionConfiguration;
import bio.terra.catalog.service.CatalogStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@TestConfiguration
class TestVersionConfig {
  @Bean
  @Primary
  public VersionConfiguration versionConfiguration() {
    VersionConfiguration mockConfig = mock(VersionConfiguration.class);
    when(mockConfig.getGitTag()).thenReturn("0.1.0");
    when(mockConfig.getGitHash()).thenReturn("hash");
    when(mockConfig.getGithub()).thenReturn("https://github.com/");
    when(mockConfig.getBuild()).thenReturn("build");
    return mockConfig;
  }
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {PublicApiController.class, TestVersionConfig.class})
@WebMvcTest
public class VersionTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private CatalogStatusService statusService;

  @Test
  public void testVersion() throws Exception {
    this.mockMvc
        .perform(get("/version"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gitTag").value("0.1.0"))
        .andExpect(jsonPath("$.gitHash").value("hash"))
        .andExpect(jsonPath("$.github").value("https://github.com/"))
        .andExpect(jsonPath("$.build").value("build"));
  }
}
