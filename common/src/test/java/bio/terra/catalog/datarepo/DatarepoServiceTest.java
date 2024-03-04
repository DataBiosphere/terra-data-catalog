package bio.terra.catalog.datarepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystemInformation;
import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.ColumnModel;
import bio.terra.datarepo.model.DatasetSummaryModel;
import bio.terra.datarepo.model.EnumerateSnapshotModel;
import bio.terra.datarepo.model.QueryDataRequestModel;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.SnapshotPreviewModel;
import bio.terra.datarepo.model.SnapshotRetrieveIncludeModel;
import bio.terra.datarepo.model.SnapshotSourceModel;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import bio.terra.datarepo.model.TableModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DatarepoServiceTest {
  private DatarepoService datarepoService;

  @Mock private DatarepoClient datarepoClient;
  @Mock private SnapshotsApi snapshotsApi;
  @Mock private UnauthenticatedApi unauthenticatedApi;

  @BeforeEach
  void beforeEach() {
    datarepoService = new DatarepoService(datarepoClient);
  }

  private void mockSnapshots() {
    when(datarepoClient.snapshotsApi()).thenReturn(snapshotsApi);
  }

  private void mockStatus() {
    when(datarepoClient.unauthenticatedApi()).thenReturn(unauthenticatedApi);
  }

  @Test
  void getSnapshots() throws Exception {
    mockSnapshots();
    UUID snapshotId = UUID.randomUUID();
    var items = Map.of(snapshotId.toString(), List.of("steward"));
    var expectedItems =
        Map.of(
            snapshotId.toString(), new StorageSystemInformation(DatasetAccessLevel.OWNER, "1234"));
    var esm =
        new EnumerateSnapshotModel()
            .items(List.of(new SnapshotSummaryModel().id(snapshotId).phsId("1234")))
            .roleMap(items);
    when(snapshotsApi.enumerateSnapshots(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(esm);
    var returnedItems = datarepoService.getDatasets();
    assertThat(returnedItems, is(expectedItems));
  }

  @Test
  void getSnapshot() throws Exception {
    mockSnapshots();
    UUID snapshotId = UUID.randomUUID();
    String phsId = "1234";
    when(snapshotsApi.retrieveSnapshot(snapshotId, List.of(SnapshotRetrieveIncludeModel.SOURCES)))
        .thenReturn(
            new SnapshotModel()
                .addSourceItem(
                    new SnapshotSourceModel().dataset(new DatasetSummaryModel().phsId(phsId))));
    when(snapshotsApi.retrieveUserSnapshotRoles(snapshotId))
        .thenReturn(List.of(DatarepoService.STEWARD_ROLE_NAME));
    assertThat(
        datarepoService.getDataset(snapshotId.toString()),
        is(new StorageSystemInformation(DatasetAccessLevel.OWNER, phsId)));
  }

  @Test
  void getSnapshotsException() throws Exception {
    mockSnapshots();
    when(snapshotsApi.enumerateSnapshots(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new ApiException());
    assertThrows(DatarepoException.class, () -> datarepoService.getDatasets());
  }

  @Test
  void getDatasetException() throws Exception {
    mockSnapshots();
    UUID snapshotId = UUID.randomUUID();
    String id = snapshotId.toString();
    when(snapshotsApi.retrieveSnapshot(snapshotId, List.of(SnapshotRetrieveIncludeModel.SOURCES)))
        .thenThrow(new ApiException());
    assertThrows(DatarepoException.class, () -> datarepoService.getDataset(id));
  }

  static Object[][] getRole() {
    return new Object[][] {
      {DatarepoService.READER_ROLE_NAME, DatasetAccessLevel.READER},
      {DatarepoService.STEWARD_ROLE_NAME, DatasetAccessLevel.OWNER},
      {"unknown role", DatasetAccessLevel.NO_ACCESS}
    };
  }

  @ParameterizedTest
  @MethodSource
  void getRole(String datarepoRole, DatasetAccessLevel expectedCatalogRole) throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id)).thenReturn(List.of(datarepoRole));
    assertThat(datarepoService.getRole(id.toString()), is(expectedCatalogRole));
  }

  @Test
  void userHasActionException() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id)).thenThrow(new ApiException());
    var stringId = id.toString();
    assertThrows(DatarepoException.class, () -> datarepoService.getRole(stringId));
  }

  @Test
  void statusOk() throws Exception {
    mockStatus();
    when(unauthenticatedApi.serviceStatus()).thenReturn(new RepositoryStatusModel().ok(true));
    assertThat(datarepoService.status(), is(new SystemStatusSystems().ok(true)));
  }

  @Test
  void statusDown() throws Exception {
    mockStatus();
    when(unauthenticatedApi.serviceStatus()).thenReturn(new RepositoryStatusModel().ok(false));
    var status = datarepoService.status();
    assertFalse(status.isOk());
  }

  @Test
  void statusException() throws Exception {
    mockStatus();
    when(unauthenticatedApi.serviceStatus()).thenThrow(new ApiException());
    var status = datarepoService.status();
    assertFalse(status.isOk());
  }

  @Test
  void getPreviewTables() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenReturn(
            new SnapshotModel()
                .tables(
                    List.of(
                        new TableModel()
                            .rowCount(1)
                            .name("table 1")
                            .columns(List.of(new ColumnModel().name("column a"))),
                        new TableModel().rowCount(0).name("table 2"))));
    assertThat(
        datarepoService.getPreviewTables(id.toString()),
        is(
            List.of(
                new TableMetadata().name("table 1").hasData(true),
                new TableMetadata().name("table 2").hasData(false))));
  }

  @Test
  void getPreviewTablesException() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    var errorMessage = "Oops, I have errored";
    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), errorMessage));
    String snapshotId = id.toString();
    DatarepoException t =
        assertThrows(DatarepoException.class, () -> datarepoService.getPreviewTables(snapshotId));

    assertThat(t.getStatusCode(), is(HttpStatus.NOT_FOUND));
    assertThat(t.getMessage(), is("bio.terra.datarepo.client.ApiException: " + errorMessage));
  }

  @Test
  void previewTable() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    var tableName = "table";
    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenReturn(
            new SnapshotModel()
                .tables(
                    List.of(
                        new TableModel()
                            .rowCount(2)
                            .name(tableName)
                            .columns(
                                List.of(new ColumnModel().name("a"), new ColumnModel().name("b"))),
                        new TableModel().rowCount(0).name("empty"))));
    List<Object> rows = List.of(Map.of("a", 1, "b", 2), Map.of("a", 3, "b", 4));
    when(snapshotsApi.querySnapshotDataById(id, tableName, new QueryDataRequestModel().limit(10)))
        .thenReturn(new SnapshotPreviewModel().result(rows));
    assertThat(
        datarepoService.previewTable(id.toString(), tableName, 10),
        is(
            new DatasetPreviewTable()
                .columns(
                    List.of(
                        new bio.terra.catalog.model.ColumnModel().name("a"),
                        new bio.terra.catalog.model.ColumnModel().name("b")))
                .rows(rows)));
  }

  @Test
  void previewTableMissingTable() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();

    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenReturn(new SnapshotModel().tables(List.of()));
    String snaphsotId = id.toString();
    assertThrows(
        NotFoundException.class, () -> datarepoService.previewTable(snaphsotId, "missing", 10));
  }

  @Test
  void previewTableException() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    var tableName = "table";
    var errorMessage = "Oops, I have errored";

    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenReturn(new SnapshotModel().tables(List.of(new TableModel().name(tableName))));
    when(snapshotsApi.querySnapshotDataById(id, tableName, new QueryDataRequestModel().limit(10)))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), errorMessage));

    String snapshotId = id.toString();
    DatarepoException t =
        assertThrows(
            DatarepoException.class, () -> datarepoService.previewTable(snapshotId, tableName, 10));

    assertThat(t.getStatusCode(), is(HttpStatus.NOT_FOUND));
    assertThat(t.getMessage(), is("bio.terra.datarepo.client.ApiException: " + errorMessage));
    assertThrows(
        NotFoundException.class, () -> datarepoService.previewTable(snapshotId, "missing", 10));
  }

  @Test
  void getExportSnapshotException() {
    String snapshotId = "snapshotId";
    String workspaceId = "workspaceId";
    assertThrows(
        BadRequestException.class,
        () -> datarepoService.exportToWorkspace(snapshotId, workspaceId));
  }
}
