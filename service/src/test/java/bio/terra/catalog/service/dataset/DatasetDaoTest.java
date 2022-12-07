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
import bio.terra.catalog.service.dataset.exception.DatasetNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DatasetDaoTest {

  @Autowired private DatasetDao datasetDao;

  private static final String METADATA =
      """
          {"sampleId": "12345", "species": ["mouse", "human"]}""";

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
  void find() {
    Dataset d1 = upsertDataset(UUID.randomUUID().toString(), StorageSystem.TERRA_DATA_REPO);
    Dataset d2 = upsertDataset(UUID.randomUUID().toString(), StorageSystem.TERRA_DATA_REPO);
    // Create a TDR dataset that we don't request in the query below.
    upsertDataset(UUID.randomUUID().toString(), StorageSystem.TERRA_DATA_REPO);
    Dataset d3 = upsertDataset(UUID.randomUUID().toString(), StorageSystem.EXTERNAL);

    Map<StorageSystem, Collection<String>> systemsAndIds =
        Map.of(
            StorageSystem.TERRA_DATA_REPO,
            List.of(d1.storageSourceId(), d2.storageSourceId()),
            StorageSystem.EXTERNAL,
            List.of(d3.storageSourceId()));
    var datasets = datasetDao.find(systemsAndIds);
    assertThat(datasets, contains(d1, d2, d3));
  }

  @Test
  void findNoIds() {
    assertThat(datasetDao.find(Map.of(StorageSystem.EXTERNAL, List.of())), empty());
  }
}
