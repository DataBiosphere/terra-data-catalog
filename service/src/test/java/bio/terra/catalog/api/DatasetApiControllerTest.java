package bio.terra.catalog.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.catalog.model.CreateDatasetRequest;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.catalog.service.DatasetService;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

  private static final String API = "/api/v1/datasets";
  private static final String API_ID = API + "/{id}";
  private static final String METADATA = """
      {"some": "metadata"}""";

  @Autowired private MockMvc mockMvc;

  @MockBean private DatasetService datasetService;

  @MockBean private SamAuthenticatedUserRequestFactory authenticatedUserRequestFactory;

  private final AuthenticatedUserRequest user = mock(AuthenticatedUserRequest.class);

  @BeforeEach
  void beforeEach() {
    when(authenticatedUserRequestFactory.getUser()).thenReturn(user);
  }

  @Test
  void listDatasets() throws Exception {
    DatasetsListResponse response = new DatasetsListResponse();
    ObjectNode node = new ObjectMapper().createObjectNode();
    node.put("id", "id");
    response.addResultItem(node);
    when(datasetService.listDatasets()).thenReturn(response);
    mockMvc
        .perform(get(API))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.result[0].id").value("id"));
  }

  @Test
  void deleteDataset() throws Exception {
    var datasetId = new DatasetId(UUID.randomUUID());
    mockMvc.perform(delete(API_ID, datasetId.uuid())).andExpect(status().is2xxSuccessful());
    verify(datasetService).deleteMetadata(datasetId);
  }

  @Test
  void getDataset() throws Exception {
    var datasetId = new DatasetId(UUID.randomUUID());
    mockMvc.perform(get(API_ID, datasetId.uuid())).andExpect(status().isOk());
    verify(datasetService).getMetadata(datasetId);
  }

  @Test
  void updateDataset() throws Exception {
    var datasetId = new DatasetId(UUID.randomUUID());
    mockMvc
        .perform(
            put(API_ID, datasetId.uuid()).contentType(MediaType.APPLICATION_JSON).content(METADATA))
        .andExpect(status().is2xxSuccessful());
    verify(datasetService).updateMetadata(datasetId, METADATA);
  }

  @Test
  void createDataset() throws Exception {
    var storageSystem = StorageSystem.EXTERNAL;
    var id = "sourceId";
    var request =
        new CreateDatasetRequest()
            .storageSystem(storageSystem.value)
            .storageSourceId(id)
            .catalogEntry(METADATA);
    var uuid = UUID.randomUUID();
    when(datasetService.createDataset(storageSystem, id, METADATA)).thenReturn(new DatasetId(uuid));
    var postBody = new ObjectMapper().writeValueAsString(request);
    mockMvc
        .perform(post(API).contentType(MediaType.APPLICATION_JSON).content(postBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(uuid.toString()));
    verify(datasetService).createDataset(storageSystem, id, METADATA);
  }

  /*
  @Test
  void createDatasetInvalidStorageSystem() throws Exception {
    var id = "sourceId";
    var request =
        new CreateDatasetRequest()
            .storageSystem("unknown")
            .storageSourceId(id)
            .catalogEntry(METADATA);
    var postBody = new ObjectMapper().writeValueAsString(request);
    mockMvc
        .perform(post(API).contentType(MediaType.APPLICATION_JSON).content(postBody))
        .andExpect(status().isInternalServerError());
  }
  */
}
