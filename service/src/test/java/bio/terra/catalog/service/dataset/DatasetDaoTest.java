package bio.terra.catalog.service.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.app.App;
import bio.terra.catalog.common.StorageSystem;
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

  private Dataset createDataset(String datasetId, StorageSystem storageSystem, String metadata)
      throws InvalidDatasetException {
    Dataset dataset = new Dataset(null, datasetId, storageSystem, metadata, null);
    return datasetDao.create(dataset);
  }

  @Test
  void testDatasetCrudOperations() {
    String datasetId = UUID.randomUUID().toString();
    String metadata = "{\"sampleId\": \"12345\", \"species\": [\"mouse\", \"human\"]}";
    Dataset dataset = createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata);
    datasetDao.retrieve(dataset.id());
    datasetDao.delete(dataset.id());
  }

  @Test
  void testCreateDatasetWithDifferentSources() throws Exception {
    String datasetId = UUID.randomUUID().toString();
    String metadata = "{\"sampleId\": \"12345\", \"species\": [\"mouse\", \"human\"]}";
    createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata);
    createDataset(datasetId, StorageSystem.TERRA_WORKSPACE, metadata);
    long datasetCount = datasetDao.enumerate().stream().map(Dataset::datasetId).count();
    assertEquals(datasetCount, 2L);
  }

  @Test
  void testCreateDuplicateDataset() throws Exception {
    String datasetId = UUID.randomUUID().toString();
    String metadata = "{\"sampleId\": \"12345\", \"species\": [\"mouse\", \"human\"]}";
    createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata);
    assertThrows(
        InvalidDatasetException.class,
        () -> createDataset(datasetId, StorageSystem.TERRA_DATA_REPO, metadata));
    long datasetCount = datasetDao.enumerate().stream().map(Dataset::datasetId).count();
    assertEquals(datasetCount, 1L);
  }
}
