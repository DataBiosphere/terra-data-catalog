package bio.terra.catalog.service.dataset;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@DataJdbcTest
@ContextConfiguration(classes = DatasetDao.class)
@EntityScan("bio.terra.catalog")
class DatasetDaoTest {

  @Autowired private DatasetDao datasetDao;

  private enum storageSystem {
    TERRA_WORKSPACE,
    TERRA_DATA_REPO
  };

  @Test
  void testCRUDMethods() {
    String datasetId = UUID.randomUUID().toString();
    Dataset dataset = new Dataset();
    dataset.setDatasetId(datasetId);
    dataset.setStorageSystem(String.valueOf(storageSystem.TERRA_WORKSPACE));
    dataset.setMetadata("{\"sampleId\": \"abc12345\"}");

    try {
      datasetDao.save(dataset);
      datasetDao.findAll();
    } finally {
      datasetDao.delete(dataset);
    }
  }
}
