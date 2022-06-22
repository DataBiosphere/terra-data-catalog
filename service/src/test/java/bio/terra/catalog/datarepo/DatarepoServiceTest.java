package bio.terra.catalog.datarepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.EnumerateSnapshotModel;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SnapshotModel;
import bio.terra.datarepo.model.SnapshotPreviewModel;
import bio.terra.datarepo.model.SnapshotRetrieveIncludeModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatarepoServiceTest {

  @Mock private SnapshotsApi snapshotsApi;
  @Mock private UnauthenticatedApi unauthenticatedApi;

  private DatarepoService datarepoService;

  @BeforeEach
  void beforeEach() {
    DatarepoService service =
        new DatarepoService(
            new DatarepoConfiguration(""), mock(SamAuthenticatedUserRequestFactory.class));
    datarepoService = spy(service);
  }

  private void mockSnapshotsApi() {
    doReturn(snapshotsApi).when(datarepoService).snapshotsApi();
  }

  private void mockUnauthenticatedApi() {
    doReturn(unauthenticatedApi).when(datarepoService).unauthenticatedApi();
  }

  @Test
  void getSnapshots() throws Exception {
    mockSnapshotsApi();
    var items = Map.of("id", List.of("steward"));
    var expectedItems = Map.of("id", DatasetAccessLevel.OWNER);
    var esm = new EnumerateSnapshotModel().roleMap(items);
    when(snapshotsApi.enumerateSnapshots(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(esm);
    assertThat(datarepoService.getSnapshotIdsAndRoles(), is(expectedItems));
  }

  @Test
  void getSnapshotsException() throws Exception {
    mockSnapshotsApi();
    when(snapshotsApi.enumerateSnapshots(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new ApiException());
    assertThrows(DatarepoException.class, () -> datarepoService.getSnapshotIdsAndRoles());
  }

  @Test
  void getRoleReader() throws Exception {
    mockSnapshotsApi();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id))
        .thenReturn(List.of(DatarepoService.READER_ROLE_NAME));
    assertThat(datarepoService.getRole(id.toString()), is(DatasetAccessLevel.READER));
  }

  @Test
  void getRoleSteward() throws Exception {
    mockSnapshotsApi();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id))
        .thenReturn(List.of(DatarepoService.STEWARD_ROLE_NAME));
    assertThat(datarepoService.getRole(id.toString()), is(DatasetAccessLevel.OWNER));
  }

  @Test
  void userHasActionException() throws Exception {
    mockSnapshotsApi();
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id)).thenThrow(new ApiException());
    var stringId = id.toString();
    assertThrows(DatarepoException.class, () -> datarepoService.getRole(stringId));
  }

  @Test
  void statusOk() throws Exception {
    mockUnauthenticatedApi();
    when(unauthenticatedApi.serviceStatus()).thenReturn(new RepositoryStatusModel().ok(true));
    assertThat(datarepoService.status(), is(new SystemStatusSystems().ok(true)));
  }

  @Test
  void statusDown() throws Exception {
    mockUnauthenticatedApi();
    when(unauthenticatedApi.serviceStatus()).thenReturn(new RepositoryStatusModel().ok(false));
    var status = datarepoService.status();
    assertFalse(status.isOk());
  }

  @Test
  void statusException() throws Exception {
    mockUnauthenticatedApi();
    when(unauthenticatedApi.serviceStatus()).thenThrow(new ApiException());
    var status = datarepoService.status();
    assertFalse(status.isOk());
  }

  @Test
  void getPreviewTables() throws Exception {
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenReturn(new SnapshotModel());
    assertThat(datarepoService.getPreviewTables(id.toString()), is(new SnapshotModel()));
  }

  @Test
  void getPreviewTablesDatarepoException() throws Exception {
    var id = UUID.randomUUID();
    var errorMessage = "Oops, I have errored";
    when(snapshotsApi.retrieveSnapshot(id, List.of(SnapshotRetrieveIncludeModel.TABLES)))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), errorMessage));
    DatarepoException t =
        assertThrows(
            DatarepoException.class, () -> datarepoService.getPreviewTables(id.toString()));

    assertThat(t.getStatusCode(), is(HttpStatus.NOT_FOUND));
    assertThat(t.getMessage(), is("bio.terra.datarepo.client.ApiException: " + errorMessage));
  }

  @Test
  void getPreviewTable() throws Exception {
    var id = UUID.randomUUID();
    var tableName = "table";
    when(snapshotsApi.lookupSnapshotPreviewById(id, tableName, null, null, null, null))
        .thenReturn(new SnapshotPreviewModel());
    assertThat(
        datarepoService.getPreviewTable(id.toString(), tableName),
        is(new SnapshotPreviewModel()));
  }

  @Test
  void getPreviewTableDatarepoException() throws Exception {
    var id = UUID.randomUUID();
    var tableName = "table";
    var errorMessage = "Oops, I have errored";

    when(snapshotsApi.lookupSnapshotPreviewById(id, tableName, null, null, null, null))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), errorMessage));

    DatarepoException t =
        assertThrows(
            DatarepoException.class,
            () -> datarepoService.getPreviewTable(id.toString(), tableName));

    assertThat(t.getStatusCode(), is(HttpStatus.NOT_FOUND));
    assertThat(t.getMessage(), is("bio.terra.datarepo.client.ApiException: " + errorMessage));
  }
}
