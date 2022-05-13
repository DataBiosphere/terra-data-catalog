package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.catalog.iam.SamAction;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceListResponse;
import bio.terra.rawls.model.WorkspaceResponse;
import java.util.List;
import java.util.Map;
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
@ContextConfiguration(classes = {RawlsService.class})
class RawlsServiceTest {
  @Autowired private RawlsService rawlsServiceReal;

  @Mock private AuthenticatedUserRequest user;

  @Mock private StatusApi statusApi;

  @Mock private WorkspacesApi workspacesApi;

  private RawlsService rawlsService;

  @BeforeEach
  void beforeEach() {
    rawlsService = spy(rawlsServiceReal);
    doReturn(workspacesApi).when(rawlsService).workspacesApi(user);
    doReturn(statusApi).when(rawlsService).statusApi();
  }

  @Test
  void status() throws Exception {
    var rawlsStatus = rawlsService.status();
    assertTrue(rawlsStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    doThrow(new ApiException()).when(statusApi).systemStatus();
    var rawlsStatus = rawlsService.status();
    assertFalse(rawlsStatus.isOk());
  }

  @Test
  void getWorkspaces() throws Exception {
    var items = Map.of("id", List.of("role"));
    var workspaceResponses =
        List.of(
            new WorkspaceListResponse()
                .workspace(new WorkspaceDetails().workspaceId("id"))
                .accessLevel(WorkspaceAccessLevel.OWNER));
    when(workspacesApi.listWorkspaces(List.of("accessLevel", "workspace.workspaceId")))
        .thenReturn(workspaceResponses);
    assertThat(rawlsService.getWorkspaceIdsAndRoles(user), is(workspaceResponses));
  }

  @Test
  void getSnapshotsException() throws Exception {
    when(workspacesApi.listWorkspaces(List.of("accessLevel", "workspace.workspaceId")))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.getWorkspaceIdsAndRoles(user));
  }

  @Test
  void userHasActionReader() throws Exception {
    var id = UUID.randomUUID();
    when(workspacesApi.getWorkspaceById(id.toString(), List.of("accessLevel")))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.READER));
    assertTrue(rawlsService.userHasAction(user, id.toString(), SamAction.READ_ANY_METADATA));
  }

  @Test
  void userHasActionWriter() throws Exception {
    var id = UUID.randomUUID();
    when(workspacesApi.getWorkspaceById(id.toString(), List.of("accessLevel")))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.WRITER));
    assertTrue(rawlsService.userHasAction(user, id.toString(), SamAction.UPDATE_ANY_METADATA));
  }

  @Test
  void userHasActionOwner() throws Exception {
    var id = UUID.randomUUID();
    when(workspacesApi.getWorkspaceById(id.toString(), List.of("accessLevel")))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.OWNER));
    assertTrue(rawlsService.userHasAction(user, id.toString(), SamAction.CREATE_METADATA));
  }

  @Test
  void userHasActionException() throws Exception {
    var id = UUID.randomUUID();
    when(workspacesApi.getWorkspaceById(id.toString(), List.of("accessLevel")))
        .thenThrow(new ApiException());
    var stringId = id.toString();
    assertThrows(
        RawlsException.class,
        () -> rawlsService.userHasAction(user, stringId, SamAction.CREATE_METADATA));
  }
}
