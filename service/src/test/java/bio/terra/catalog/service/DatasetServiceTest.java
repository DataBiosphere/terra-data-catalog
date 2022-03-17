package bio.terra.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

  @MockBean private DatasetDao datasetDao;

  @MockBean private ObjectMapper objectMapper;

  @MockBean private SamService samService;

  private final AuthenticatedUserRequest user = mock(AuthenticatedUserRequest.class);
  private final DatasetId datasetId = new DatasetId(UUID.randomUUID());
  private final Dataset dataset = new Dataset(null, StorageSystem.TERRA_DATA_REPO, null);

  @BeforeEach
  public void setup() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
  }

  @Test
  void testListDatasets() {}

  @Test()
  void testDeleteMetadataWithInvalidUser() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
    assertThrows(UnauthorizedException.class, () -> datasetService.deleteMetadata(user, datasetId));
  }

  @Test()
  void testDeleteMetadata() {
    when(samService.hasAction(user, SamAction.DELETE_ANY_METADATA)).thenReturn(true);
    datasetService.deleteMetadata(user, datasetId);
    verify(datasetDao).delete(dataset);
  }

  @Test
  void testGetMetadataWithInvalidUser() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
    assertThrows(UnauthorizedException.class, () -> datasetService.getMetadata(user, datasetId));
  }

  @Test
  void testGetMetadata() {
    when(samService.hasAction(user, SamAction.READ_ANY_METADATA)).thenReturn(true);
    datasetService.getMetadata(user, datasetId);
    verify(datasetDao).retrieve(datasetId);
  }

  @Test
  void testUpdateMetadataWithInvalidUser() {
    when(datasetDao.retrieve(datasetId)).thenReturn(dataset);
    assertThrows(
        UnauthorizedException.class, () -> datasetService.updateMetadata(user, datasetId, "test"));
  }

  @Test
  void testUpdateMetadata() {
    String metadata = "test metadata";
    when(samService.hasAction(user, SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    datasetService.updateMetadata(user, datasetId, metadata);
    verify(datasetDao).update(dataset.withMetadata(metadata));
  }

  @Test
  void testCreateDatasetWithInvalidUser() {
    assertThrows(
        UnauthorizedException.class,
        () -> datasetService.createDataset(user, StorageSystem.TERRA_DATA_REPO, null, null));
  }

  @Test
  void testCreateDataset() {
    String metadata = "test metadata";
    String storageSourceId = "testSource";
    Dataset testDataset = new Dataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, metadata);
    Dataset testDatasetWithCreationInfo =
        new Dataset(
            datasetId, storageSourceId, StorageSystem.TERRA_DATA_REPO, metadata, Instant.now());

    when(samService.hasAction(user, SamAction.UPDATE_ANY_METADATA)).thenReturn(true);
    when(datasetDao.create(testDataset)).thenReturn(testDatasetWithCreationInfo);
    DatasetId id =
        datasetService.createDataset(
            user, StorageSystem.TERRA_DATA_REPO, storageSourceId, metadata);
    verify(datasetDao).create(testDataset);
    assertEquals(id, datasetId);
  }
}
