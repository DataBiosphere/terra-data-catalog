package bio.terra.catalog.service.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.app.App;
import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.service.dataset.exception.InvalidDatasetException;
import java.time.Instant;
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

  @Test
  void testCreateDatasetWithDifferentSources() throws Exception {
    String datasetId = UUID.randomUUID().toString();
    Dataset dataset =
        new Dataset(
            null,
            datasetId,
            StorageSystem.TERRA_DATA_REPO,
            "{\"sampleId\": \"12345\", \"species\": [\"mouse\", \"human\"]}",
            null);
    Dataset duplicateDataset =
        new Dataset(
            null,
            datasetId,
            StorageSystem.TERRA_WORKSPACE,
            "{\"sampleId\": \"12345\", \"species\": [\"mouse\", \"human\"]}",
            null);
    try {
      datasetDao.create(dataset);
      datasetDao.create(duplicateDataset);
      int datasetCount = datasetDao.enumerate().size();
      assertEquals(datasetCount, 2);
    } finally {
      datasetDao.enumerate().forEach(d -> datasetDao.delete(d.id()));
    }
  }

  @Test
  void testCreateDuplicateDataset() throws Exception {
    String datasetId = UUID.randomUUID().toString();
    Dataset dataset =
        new Dataset(
            null,
            datasetId,
            StorageSystem.TERRA_DATA_REPO,
            "{\"sampleId\": \"12345\", \"species\": [\"mouse\", \"human\"]}",
            null);
    try {
      datasetDao.create(dataset);
      assertThrows(InvalidDatasetException.class, () -> datasetDao.create(dataset));
      int datasetCount = datasetDao.enumerate().size();
      assertEquals(datasetCount, 1);
    } finally {
      datasetDao.delete(datasetDao.enumerate().get(0).id());
    }
  }
}
