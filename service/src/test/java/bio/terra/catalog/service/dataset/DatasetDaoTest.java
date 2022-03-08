package bio.terra.catalog.service.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.app.App;
import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.service.dataset.exception.DatasetNotFoundException;
import bio.terra.catalog.service.dataset.exception.InvalidDatasetException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = App.class)
class DatasetDaoTest {

  @Autowired private DatasetDao datasetDao;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  private static final String metadata =
      """
      {"sampleId": "12345", "species": ["mouse", "human"]}""";

  private Dataset createDataset(String datasetId, StorageSystem storageSystem, String metadata)
      throws InvalidDatasetException {
    Dataset dataset = new Dataset(null, datasetId, storageSystem, metadata, null);
    return datasetDao.create(dataset);
  }

  @Test
  void testDatasetCrudOperations() {
    String datasetId = UUID.randomUUID().toString();
    Dataset dataset = createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata);
    Dataset updateRequest =
        new Dataset(dataset.id(), datasetId, StorageSystem.TERRA_WORKSPACE, metadata, null);
    datasetDao.retrieve(dataset.id());
    Dataset updatedDataset = datasetDao.update(updateRequest);
    assertEquals(updatedDataset.storageSystem(), updateRequest.storageSystem());
    datasetDao.delete(dataset.id());
    assertThrows(DatasetNotFoundException.class, () -> datasetDao.retrieve(dataset.id()));
  }

  @Test
  void testCreateDatasetWithDifferentSources() {
    String datasetId = UUID.randomUUID().toString();
    createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata);
    createDataset(datasetId, StorageSystem.TERRA_WORKSPACE, metadata);
    long datasetCount =
        datasetDao.enumerate().stream()
            .filter(dataset -> dataset.datasetId().equals(datasetId))
            .count();
    assertEquals(2L, datasetCount);
  }

  @Test
  void testCreateDuplicateDataset() {
    String datasetId = UUID.randomUUID().toString();
    createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata);
    assertThrows(
        InvalidDatasetException.class,
        () -> createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata));
    long datasetCount =
        datasetDao.enumerate().stream()
            .filter(dataset -> dataset.datasetId().equals(datasetId))
            .count();
    assertEquals(1L, datasetCount);
  }
}
