package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.RawlsConfiguration;
import bio.terra.catalog.iam.SamAuthenticatedUserRequestFactory;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceListResponse;
import bio.terra.rawls.model.WorkspaceResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RawlsServiceTest {
  @Mock private StatusApi statusApi;

  @Mock private WorkspacesApi workspacesApi;

  private RawlsService rawlsService;

  @BeforeEach
  void beforeEach() {
    rawlsService =
        spy(
            new RawlsService(
                new RawlsConfiguration(""), mock(SamAuthenticatedUserRequestFactory.class)));
  }

  private void mockWorkspacesApi() {
    doReturn(workspacesApi).when(rawlsService).workspacesApi();
  }

  private void mockStatusApi() {
    doReturn(statusApi).when(rawlsService).statusApi();
  }

  @Test
  void status() {
    mockStatusApi();
    var rawlsStatus = rawlsService.status();
    assertTrue(rawlsStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    mockStatusApi();
    doThrow(new ApiException()).when(statusApi).systemStatus();
    var rawlsStatus = rawlsService.status();
    assertFalse(rawlsStatus.isOk());
  }

  @Test
  void getWorkspaces() throws Exception {
    mockWorkspacesApi();
    var items = Map.of("id", DatasetAccessLevel.OWNER);
    var workspaceResponses =
        List.of(
            new WorkspaceListResponse()
                .workspace(new WorkspaceDetails().workspaceId("id"))
                .accessLevel(WorkspaceAccessLevel.OWNER));
    when(workspacesApi.listWorkspaces(RawlsService.ACCESS_LEVEL_AND_ID))
        .thenReturn(workspaceResponses);
    assertThat(rawlsService.getWorkspaceIdsAndRoles(), is(items));
  }

  @Test
  void getWorkspacesException() throws Exception {
    mockWorkspacesApi();
    when(workspacesApi.listWorkspaces(RawlsService.ACCESS_LEVEL_AND_ID))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.getWorkspaceIdsAndRoles());
  }

  @Test
  void getRoleReader() throws Exception {
    mockWorkspacesApi();
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.READER));
    assertThat(rawlsService.getRole(id), is(DatasetAccessLevel.READER));
  }

  @Test
  void getRoleOwner() throws Exception {
    mockWorkspacesApi();
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.OWNER));
    assertThat(rawlsService.getRole(id), is(DatasetAccessLevel.OWNER));
  }

  @Test
  void userHasActionException() throws Exception {
    mockWorkspacesApi();
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.getRole(id));
  }
}
