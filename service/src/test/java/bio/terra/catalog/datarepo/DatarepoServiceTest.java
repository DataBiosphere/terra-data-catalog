package bio.terra.catalog.datarepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.service.dataset.Dataset;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.ColumnModel;
import bio.terra.datarepo.model.EnumerateSnapshotModel;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.SnapshotPreviewModel;
import bio.terra.datarepo.model.SnapshotRetrieveIncludeModel;
import bio.terra.datarepo.model.TableDataType;
import bio.terra.datarepo.model.TableModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DatarepoServiceTest {
  private DatarepoService datarepoService;

  @Mock private DatarepoClient datarepoClient;
  @Mock private AuthenticatedUserRequest user;
  @Mock private SnapshotsApi snapshotsApi;
  @Mock private UnauthenticatedApi unauthenticatedApi;

  @BeforeEach
  void beforeEach() {
    datarepoService = new DatarepoService(datarepoClient);
  }

  private void mockSnapshots() {
    when(datarepoClient.snapshotsApi(user)).thenReturn(snapshotsApi);
  }

  private void mockStatus() {
    when(datarepoClient.unauthenticatedApi()).thenReturn(unauthenticatedApi);
  }

  @Test
  void getSnapshots() throws Exception {
    mockSnapshots();
    var items = Map.of("id", List.of("steward"));
    var expectedItems = Map.of("id", DatasetAccessLevel.OWNER);
    var esm = new EnumerateSnapshotModel().roleMap(items);
    when(snapshotsApi.enumerateSnapshots(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(esm);
    assertThat(datarepoService.getIdsAndRoles(user), is(expectedItems));
  }

  @Test
  void getSnapshotsException() throws Exception {
    mockSnapshots();
    when(snapshotsApi.enumerateSnapshots(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new ApiException());
    assertThrows(DatarepoException.class, () -> datarepoService.getIdsAndRoles(user));
  }

  @Test
  void getRoleReader() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id))
        .thenReturn(List.of(DatarepoService.READER_ROLE_NAME));
    assertThat(datarepoService.getRole(user, id.toString()), is(DatasetAccessLevel.READER));
  }

  @Test
  void getRoleSteward() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id))
        .thenReturn(List.of(DatarepoService.STEWARD_ROLE_NAME));
    assertThat(datarepoService.getRole(user, id.toString()), is(DatasetAccessLevel.OWNER));
  }

  @Test
  void userHasActionException() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id)).thenThrow(new ApiException());
    var stringId = id.toString();
    assertThrows(DatarepoException.class, () -> datarepoService.getRole(user, stringId));
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
        .thenReturn(new SnapshotModel());
    assertThat(datarepoService.getPreviewTables(user, id.toString()), is(new SnapshotModel()));
  }

  @Test
  void getPreviewTablesDatarepoException() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    var errorMessage = "Oops, I have errored";
    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), errorMessage));
    String snapshotId = id.toString();
    DatarepoException t =
        assertThrows(
            DatarepoException.class, () -> datarepoService.getPreviewTables(user, snapshotId));

    assertThat(t.getStatusCode(), is(HttpStatus.NOT_FOUND));
    assertThat(t.getMessage(), is("bio.terra.datarepo.client.ApiException: " + errorMessage));
  }

  @Test
  void getPreviewTable() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    var tableName = "table";
    when(snapshotsApi.lookupSnapshotPreviewById(id, tableName, null, 10, null, null))
        .thenReturn(new SnapshotPreviewModel());
    assertThat(
        datarepoService.getPreviewTable(user, id.toString(), tableName, 10),
        is(new SnapshotPreviewModel()));
  }

  @Test
  void getPreviewTableDatarepoException() throws Exception {
    mockSnapshots();
    var id = UUID.randomUUID();
    var tableName = "table";
    var errorMessage = "Oops, I have errored";

    when(snapshotsApi.lookupSnapshotPreviewById(id, tableName, null, 10, null, null))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), errorMessage));

    String snapshotId = id.toString();
    DatarepoException t =
        assertThrows(
            DatarepoException.class,
            () -> datarepoService.getPreviewTable(user, snapshotId, tableName, 10));

    assertThat(t.getStatusCode(), is(HttpStatus.NOT_FOUND));
    assertThat(t.getMessage(), is("bio.terra.datarepo.client.ApiException: " + errorMessage));
  }

  @Test
  void getExportSnapshotException() {
    String snapshotId = "snapshotId";
    String workspaceId = "workspaceId";
    assertThrows(
        BadRequestException.class,
        () -> datarepoService.exportToWorkspace(user, snapshotId, workspaceId));
  }

  @Test
  void getPreviewTables() {
    var tdrDataset =
        new Dataset(dataset.id(), sourceId, StorageSystem.TERRA_DATA_REPO, metadata, null);
    when(datasetDao.retrieve(datasetId)).thenReturn(tdrDataset);
    when(datarepoService.getPreviewTables(user, tdrDataset.storageSourceId()))
        .thenReturn(
            new SnapshotModel()
                .tables(
                    List.of(
                        new TableModel()
                            .rowCount(1)
                            .columns(
                                List.of(
                                    new ColumnModel()
                                        .datatype(TableDataType.INTEGER)
                                        .name("column a"))))));
  }
}
