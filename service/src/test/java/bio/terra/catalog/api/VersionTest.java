package bio.terra.catalog.api;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PublicApiController.class)
@WebMvcTest
public class VersionTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private VersionConfiguration versionConfiguration;

  @MockBean private CatalogStatusService statusService;

  @Test
  public void testVersion() throws Exception {
    String gitTag = "0.1.0";
    String gitHash = "abc1234";
    String github = "https://github.com/DataBiosphere/terra-data-catalog/tree/0.9.0";
    String build = "0.1.0";

    when(versionConfiguration.getGitTag()).thenReturn(gitTag);
    when(versionConfiguration.getGitHash()).thenReturn(gitHash);
    when(versionConfiguration.getGithub()).thenReturn(github);
    when(versionConfiguration.getBuild()).thenReturn(build);

    this.mockMvc
        .perform(get("/version"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gitTag").value(gitTag))
        .andExpect(jsonPath("$.gitHash").value(gitHash))
        .andExpect(jsonPath("$.github").value(github))
        .andExpect(jsonPath("$.build").value(build));
  }
}
