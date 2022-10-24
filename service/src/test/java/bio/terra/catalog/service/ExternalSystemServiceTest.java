package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.catalog.service.dataset.DatasetDao;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalSystemServiceTest {
  @Mock private DatasetDao mockDatasetDao;

  @Test
  void getDatasets() {
    String id1 = "storageId1";
    String id2 = "storageId2";
    List<Dataset> resultDatasets =
        List.of(
            new Dataset(id1, StorageSystem.EXTERNAL, ""),
            new Dataset(id2, StorageSystem.EXTERNAL, ""));
    when(mockDatasetDao.listAllExternalDatasets()).thenReturn(resultDatasets);

    var expectedStorageSystemInformation =
        new StorageSystemInformation(DatasetAccessLevel.DISCOVERER, null);
    var externalSystemService = new ExternalSystemService(mockDatasetDao);
    assertThat(
        externalSystemService.getDatasets(null),
        hasEntry(is(id1), is(expectedStorageSystemInformation)));
    assertThat(
        externalSystemService.getDatasets(null),
        hasEntry(is(id2), is(expectedStorageSystemInformation)));
  }
}
