package bio.terra.catalog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.service.DatasetService;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.common.iam.AuthenticatedUserRequestFactory;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DatasetApiController.class, DatasetService.class})
@WebMvcTest
class DatasetApiControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private DatarepoService datarepoService;

  @MockBean private AuthenticatedUserRequestFactory authenticatedUserRequestFactory;

  @BeforeEach
  void beforeEach() {
    when(authenticatedUserRequestFactory.from(any()))
        .thenReturn(mock(AuthenticatedUserRequest.class));
  }

  @Test
  void listDatasets() throws Exception {
    var snapshot = new SnapshotSummaryModel().id(UUID.randomUUID()).name("snapshot");
    when(datarepoService.getSnapshots(any())).thenReturn(List.of(snapshot));
    mockMvc
        .perform(get("/api/v1/datasets"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.result[0].id").value(snapshot.getId().toString()))
        .andExpect(jsonPath("$.result[0].dct:title").value(snapshot.getName()));
  }
}
