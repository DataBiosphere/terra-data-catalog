package bio.terra.catalog.service.dataset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.config.CatalogDatabaseConfiguration;
import bio.terra.catalog.service.dataset.exception.DatasetNotFoundException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DatasetDaoTest {

  private DatasetDao datasetDao;
  private DataSource dataSource;

  private static final String METADATA =
      """
          {"sampleId": "12345", "species": ["mouse", "human"]}""";

  @BeforeEach
  public void beforeEach() {
    var config = new CatalogDatabaseConfiguration();
    config.setUsername("dbuser");
    config.setPassword("dbpwd");
    config.setUri("jdbc:postgresql://127.0.0.1:5432/catalog_db");
    dataSource = config.getDataSource();
    datasetDao = new DatasetDao(new NamedParameterJdbcTemplate(dataSource));
  }

  @AfterEach
  public void afterEach() throws SQLException {
    try (var statement = dataSource.getConnection().createStatement()) {
      statement.execute("truncate table dataset");
    }
  }

  private Dataset upsertDataset(String storageSourceId, StorageSystem storageSystem) {
    return upsertDataset(storageSourceId, storageSystem, DatasetDaoTest.METADATA);
  }

  private Dataset upsertDataset(
      String storageSourceId, StorageSystem storageSystem, String metadata) {
    return datasetDao.upsert(new Dataset(storageSourceId, storageSystem, metadata));
  }

  @Test
  void testListAllExternalDatasets() {
    String storageSourceId = UUID.randomUUID().toString();
    for (StorageSystem value : StorageSystem.values()) {
      upsertDataset(storageSourceId, value);
    }
    List<Dataset> datasets =
        datasetDao.listAllDatasets(StorageSystem.EXTERNAL).stream()
            .filter(dataset -> dataset.storageSourceId().equals(storageSourceId))
            .toList();
    assertThat(datasets, hasSize(1));
    Dataset dataset = datasets.get(0);
    assertThat(dataset.storageSourceId(), is(storageSourceId));
    assertThat(dataset.storageSystem(), is(StorageSystem.EXTERNAL));
  }

  @Test
  void testDatasetCrudOperations() {
    String storageSourceId = UUID.randomUUID().toString();
    Dataset dataset = upsertDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO);
    var newMetadata = "{}";
    Dataset updateRequest = dataset.withMetadata(newMetadata);
    datasetDao.update(updateRequest);
    DatasetId id = dataset.id();
    assertEquals(newMetadata, datasetDao.retrieve(id).metadata());
    assertTrue(datasetDao.delete(dataset));
    assertThrows(DatasetNotFoundException.class, () -> datasetDao.retrieve(id));
  }

  @Test
  void testCreateDatasetWithDifferentSources() {
    String storageSourceId = UUID.randomUUID().toString();
    for (StorageSystem value : StorageSystem.values()) {
      upsertDataset(storageSourceId, value);
    }
    List<Dataset> datasets =
        datasetDao.listAllDatasets().stream()
            .filter(dataset -> dataset.storageSourceId().equals(storageSourceId))
            .toList();
    assertThat(datasets, hasSize(StorageSystem.values().length));
    datasets.forEach(dataset -> assertThat(dataset.storageSourceId(), is(storageSourceId)));
  }

  @Test
  void testCreateDuplicateDataset() {
    String storageSourceId = UUID.randomUUID().toString();
    upsertDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO);
    Dataset dataset = upsertDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO, "{}");

    assertThat(dataset.metadata(), is("{}"));
  }

  @Test
  void testHandleNonExistentDatasets() {
    DatasetId id = new DatasetId(UUID.randomUUID());
    Dataset dataset = new Dataset(id, "source id", StorageSystem.TERRA_WORKSPACE, METADATA, null);
    assertThrows(DatasetNotFoundException.class, () -> datasetDao.retrieve(id));
    assertThrows(DatasetNotFoundException.class, () -> datasetDao.update(dataset));
    assertFalse(datasetDao.delete(dataset));
  }

  @Test
  void testFind() {
    String storageSourceId = UUID.randomUUID().toString();
    Dataset d1 = upsertDataset(storageSourceId, StorageSystem.TERRA_DATA_REPO);
    // Create a TDR dataset that we don't request in the query below.
    String storageSourceId2 = UUID.randomUUID().toString();
    upsertDataset(storageSourceId2, StorageSystem.TERRA_DATA_REPO);
    Dataset d3 = upsertDataset(storageSourceId, StorageSystem.EXTERNAL);
    var datasets =
        datasetDao.find(
            StorageSystem.TERRA_DATA_REPO, List.of(d1.storageSourceId(), d3.storageSourceId()));
    assertThat(datasets, contains(d1));
  }

  @Test
  void testFindNoIds() {
    assertThat(datasetDao.find(StorageSystem.EXTERNAL, List.of()), empty());
  }
}
