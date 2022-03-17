package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = {DatasetService.class})
class DatasetServiceTest {

  @Autowired private DatasetService datasetService;

  @MockBean private DatarepoService datarepoService;

  @MockBean private SamService samService;

  @MockBean private DatasetDao datasetDao;

  @MockBean private ObjectMapper objectMapper;

  @MockBean private SamAuthenticatedUserRequestFactory userFactory;

  private final AuthenticatedUserRequest user = mock(AuthenticatedUserRequest.class);

  private final DatasetId datasetId = new DatasetId(UUID.randomUUID());
  private final Dataset dataset =
      new Dataset(datasetId, null, StorageSystem.TERRA_DATA_REPO, null, null);

  @BeforeEach
  void beforeEach() {
    when(userFactory.getUser()).thenReturn(user);
  }

  @Test
  void listDatasets() {
    var result = datasetService.listDatasets();
    assertThat(result, is(notNullValue()));
  }

  @Test
  void deleteMetadata() {
    when(samService.hasAction(SamAction.DELETE_ANY_METADATA)).thenReturn(true);
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
    datasetService.deleteMetadata(datasetId);
  }

  @Test
  void getMetadata() {
    when(samService.hasAction(SamAction.READ_ANY_METADATA)).thenReturn(true);
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
    datasetService.getMetadata(datasetId);
  }

  @Test
  void updateMetadata() {
    when(samService.hasAction(SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
    datasetService.updateMetadata(datasetId, "test");
  }

  @Test
  void createDataset() {}
}
