package bio.terra.catalog.service.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.catalog.app.App;
import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.service.dataset.exception.DatasetNotFoundException;
import bio.terra.catalog.service.dataset.exception.InvalidDatasetException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = App.class)
class DatasetDaoTest {

  @Autowired private DatasetDao datasetDao;

  @MockBean HttpServletRequest httpServletRequest;

  private static final String METADATA =
      """
      {"sampleId": "12345", "species": ["mouse", "human"]}""";

  private Dataset createDataset(
      String storageSourceId, StorageSystem storageSystem, String metadata)
      throws InvalidDatasetException {
    Dataset dataset = new Dataset(null, storageSourceId, storageSystem, metadata, null);
    return datasetDao.create(dataset);
  }

  @Test
  void testDatasetCrudOperations() {
    String storageSourceId = UUID.randomUUID().toString();
    Dataset dataset = createDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, METADATA);
    DatasetId id = dataset.id();
    Dataset updateRequest =
        new Dataset(id, storageSourceId, StorageSystem.TERRA_WORKSPACE, METADATA, null);
    datasetDao.retrieve(id);
    Dataset updatedDataset = datasetDao.update(updateRequest);
    assertEquals(updatedDataset.storageSystem(), updateRequest.storageSystem());
    assertTrue(datasetDao.delete(dataset));
    assertThrows(DatasetNotFoundException.class, () -> datasetDao.retrieve(id));
  }

  @Test
  void testCreateDatasetWithDifferentSources() {
    String storageSourceId = UUID.randomUUID().toString();
    createDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, METADATA);
    createDataset(storageSourceId, StorageSystem.TERRA_WORKSPACE, METADATA);
    long datasetCount =
        datasetDao.enumerate().stream()
            .filter(dataset -> dataset.storageSourceId().equals(storageSourceId))
            .count();
    assertEquals(2L, datasetCount);
  }

  @Test
  void testCreateDuplicateDataset() {
    String storageSourceId = UUID.randomUUID().toString();
    createDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, METADATA);
    assertThrows(
        InvalidDatasetException.class,
        () -> createDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, METADATA));
    long datasetCount =
        datasetDao.enumerate().stream()
            .filter(dataset -> dataset.storageSourceId().equals(storageSourceId))
            .count();
    assertEquals(1L, datasetCount);
  }

  @Test
  void testHandleNonExistentDatasets() {
    DatasetId id = new DatasetId(UUID.randomUUID());
    String storageSourceId = UUID.randomUUID().toString();
    Dataset dataset =
        new Dataset(id, storageSourceId, StorageSystem.TERRA_WORKSPACE, METADATA, null);
    assertThrows(DatasetNotFoundException.class, () -> datasetDao.retrieve(id));
    assertThrows(DatasetNotFoundException.class, () -> datasetDao.update(dataset));
    assertFalse(datasetDao.delete(dataset));
  }
}
