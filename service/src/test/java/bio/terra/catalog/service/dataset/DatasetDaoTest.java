package bio.terra.catalog.service.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.app.App;
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

  private enum storageSystem {
    TERRA_WORKSPACE,
    TERRA_DATA_REPO
  };

  @Test
  void testCreateDeleteDataset() throws Exception {
    String datasetId = UUID.randomUUID().toString();
    Dataset dataset =
        new Dataset()
            .datasetId(datasetId)
            .storageSystem("DATA_REPO")
            .metadata("{\"sampleId\": \"12345\", \"species\": [\"mouse\", \"human\"]}");
    Dataset duplicateDatasetId =
        new Dataset()
            .datasetId(datasetId)
            .storageSystem(String.valueOf(storageSystem.TERRA_WORKSPACE));
    try {
      datasetDao.create(dataset);
      datasetDao.create(duplicateDatasetId);
      int datasetCount = datasetDao.enumerate().size();
      assertEquals(datasetCount, 2);
    } finally {
      datasetDao.enumerate().forEach(d -> datasetDao.delete(d.getId()));
    }
  }

  @Test
  void testCreateDuplicateDataset() throws Exception {
    String datasetId = UUID.randomUUID().toString();
    Dataset dataset =
        new Dataset()
            .datasetId(datasetId)
            .storageSystem(String.valueOf(storageSystem.TERRA_DATA_REPO));
    try {
      datasetDao.create(dataset);
      assertThrows(InvalidDatasetException.class, () -> datasetDao.create(dataset));
      int datasetCount = datasetDao.enumerate().size();
      assertEquals(datasetCount, 1);
    } finally {
      datasetDao.delete(datasetDao.enumerate().get(0).getId());
    }
  }
}
