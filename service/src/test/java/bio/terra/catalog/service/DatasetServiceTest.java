package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import bio.terra.catalog.config.BeanConfig;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.iam.AuthenticatedUserRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = {DatasetService.class, BeanConfig.class})
class DatasetServiceTest {

  @Autowired private DatasetService datasetService;

  @MockBean private DatarepoService datarepoService;

  private final AuthenticatedUserRequest user = mock(AuthenticatedUserRequest.class);

  private final DatasetId datasetId = new DatasetId(UUID.randomUUID());

  @Test
  void listDatasets() {
    var result = datasetService.listDatasets(user);
    assertThat(result, is(notNullValue()));
  }

  @Test
  void deleteMetadata() {
    datasetService.deleteMetadata(user, datasetId);
  }

  @Test
  void getMetadata() {
    datasetService.getMetadata(user, datasetId);
  }

  @Test
  void updateMetadata() {
    datasetService.updateMetadata(user, datasetId, "test");
  }

  @Test
  void createDataset() {

  }
}
