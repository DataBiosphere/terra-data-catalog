package bio.terra.catalog.datarepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.catalog.iam.SamAction;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.api.SnapshotsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.EnumerateSnapshotModel;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ConfigurationPropertiesScan("bio.terra.catalog")
@ContextConfiguration(classes = {DatarepoService.class})
class DatarepoServiceTest {

  @Autowired private DatarepoService datarepoServiceReal;

  @Mock private AuthenticatedUserRequest user;
  @Mock private SnapshotsApi snapshotsApi;
  @Mock private UnauthenticatedApi unauthenticatedApi;

  private DatarepoService datarepoService;

  @BeforeEach
  void beforeEach() {
    datarepoService = spy(datarepoServiceReal);
    doReturn(snapshotsApi).when(datarepoService).snapshotsApi(user);
    doReturn(unauthenticatedApi).when(datarepoService).unauthenticatedApi();
  }

  @Test
  void getSnapshots() throws Exception {
    var items = List.of(new SnapshotSummaryModel());
    var esm = new EnumerateSnapshotModel().items(items);
    when(snapshotsApi.enumerateSnapshots(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(esm);
    assertThat(datarepoService.getSnapshots(user), is(items));
  }

  @Test
  void getSnapshotsException() throws Exception {
    when(snapshotsApi.enumerateSnapshots(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new ApiException());
    assertThrows(DatarepoException.class, () -> datarepoService.getSnapshots(user));
  }

  @Test
  void userHasActionReader() throws Exception {
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id))
        .thenReturn(List.of(DatarepoService.READER_ROLE_NAME));
    assertThat(
        datarepoService.userHasAction(user, id.toString(), SamAction.READ_ANY_METADATA), is(true));
  }

  @Test
  void userHasActionOwner() throws Exception {
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id))
        .thenReturn(List.of(DatarepoService.ADMIN_ROLE_NAME));
    assertThat(
        datarepoService.userHasAction(user, id.toString(), SamAction.CREATE_METADATA), is(true));
  }

  @Test
  void userHasActionException() throws Exception {
    var id = UUID.randomUUID();
    when(snapshotsApi.retrieveUserSnapshotRoles(id)).thenThrow(new ApiException());
    assertThrows(
        DatarepoException.class,
        () -> datarepoService.userHasAction(user, id.toString(), SamAction.CREATE_METADATA));
  }

  @Test
  void statusOk() throws Exception {
    when(unauthenticatedApi.serviceStatus()).thenReturn(new RepositoryStatusModel().ok(true));
    assertThat(datarepoService.status(), is(new SystemStatusSystems().ok(true)));
  }

  @Test
  void statusDown() throws Exception {
    when(unauthenticatedApi.serviceStatus()).thenReturn(new RepositoryStatusModel().ok(false));
    var status = datarepoService.status();
    assertFalse(status.isOk());
  }

  @Test
  void statusException() throws Exception {
    when(unauthenticatedApi.serviceStatus()).thenThrow(new ApiException());
    var status = datarepoService.status();
    assertFalse(status.isOk());
  }
}
