package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.config.BeanConfig;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.catalog.service.dataset.DatasetDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalSystemServiceTest {
  @Mock private DatasetDao mockDatasetDao;

  private static final ObjectMapper objectMapper = new BeanConfig().objectMapper();

  @Test
  void getDatasets() {
    String storageSourceId = "source id";
    List<Dataset> resultDatasets =
        List.of(
            new Dataset(storageSourceId, StorageSystem.EXTERNAL, objectMapper.createObjectNode()));
    when(mockDatasetDao.listAllDatasets(StorageSystem.EXTERNAL)).thenReturn(resultDatasets);

    var expectedStorageSystemInformation =
        new StorageSystemInformation(DatasetAccessLevel.DISCOVERER);
    var externalSystemService = new ExternalSystemService(mockDatasetDao);
    assertThat(
        externalSystemService.getDatasets(),
        hasEntry(is(storageSourceId), is(expectedStorageSystemInformation)));
  }

  @Test
  void getDataset() {
    var externalSystemService = new ExternalSystemService(mockDatasetDao);
    String storageSourceId = "source id";
    assertThat(
        externalSystemService.getDataset(storageSourceId),
        is(new StorageSystemInformation(DatasetAccessLevel.DISCOVERER)));
  }
}
