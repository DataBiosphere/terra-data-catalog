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

import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.EntitiesApi;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.EntityQueryResponse;
import bio.terra.rawls.model.EntityTypeMetadata;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceListResponse;
import bio.terra.rawls.model.WorkspaceResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  @Mock private EntitiesApi entitiesApi;

  private RawlsService rawlsService;

  @BeforeEach
  void beforeEach() {
    rawlsService = spy(rawlsServiceReal);
    doReturn(workspacesApi).when(rawlsService).workspacesApi(user);
    doReturn(statusApi).when(rawlsService).statusApi();
    doReturn(entitiesApi).when(rawlsService).entitiesApi(user);
  }

  @Test
  void status() {
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
    var items = Map.of("id", DatasetAccessLevel.OWNER);
    var workspaceResponses =
        List.of(
            new WorkspaceListResponse()
                .workspace(new WorkspaceDetails().workspaceId("id"))
                .accessLevel(WorkspaceAccessLevel.OWNER));
    when(workspacesApi.listWorkspaces(RawlsService.ACCESS_LEVEL_AND_ID))
        .thenReturn(workspaceResponses);
    assertThat(rawlsService.getWorkspaceIdsAndRoles(user), is(items));
  }

  @Test
  void getSnapshotsException() throws Exception {
    when(workspacesApi.listWorkspaces(RawlsService.ACCESS_LEVEL_AND_ID))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.getWorkspaceIdsAndRoles(user));
  }

  @Test
  void getRoleReader() throws Exception {
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.READER));
    assertThat(rawlsService.getRole(user, id), is(DatasetAccessLevel.READER));
  }

  @Test
  void getRoleOwner() throws Exception {
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.OWNER));
    assertThat(rawlsService.getRole(user, id), is(DatasetAccessLevel.OWNER));
  }

  @Test
  void userHasActionException() throws Exception {
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.getRole(user, id));
  }

  @Test
  void entityQuery() throws Exception {
    String id = "abc";
    String name = "name";
    String namespace = "namespace";
    String tableName = "table";
    WorkspaceResponse response =
        new WorkspaceResponse().workspace(new WorkspaceDetails().name(name).namespace(namespace));
    when(workspacesApi.getWorkspaceById(id, List.of())).thenReturn(response);
    EntityQueryResponse queryResponse = new EntityQueryResponse();
    when(entitiesApi.entityQuery(
            namespace, name, tableName, null, null, null, null, null, List.of(), null, null))
        .thenReturn(queryResponse);
    assertThat(rawlsService.entityQuery(user, id, tableName), is(queryResponse));
  }

  @Test
  void entityQueryException() throws Exception {
    String id = "abc";
    String name = "name";
    String namespace = "namespace";
    String tableName = "table";
    WorkspaceResponse response =
        new WorkspaceResponse().workspace(new WorkspaceDetails().name(name).namespace(namespace));
    when(workspacesApi.getWorkspaceById(id, List.of())).thenReturn(response);
    when(entitiesApi.entityQuery(
            namespace, name, tableName, null, null, null, null, null, List.of(), null, null))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.entityQuery(user, id, tableName));
  }

  @Test
  void entityMetadata() throws Exception {
    String id = "abc";
    String name = "name";
    String namespace = "namespace";
    WorkspaceResponse response =
        new WorkspaceResponse().workspace(new WorkspaceDetails().name(name).namespace(namespace));
    when(workspacesApi.getWorkspaceById(id, List.of())).thenReturn(response);
    Map<String, EntityTypeMetadata> queryResponse = new HashMap<>();
    when(entitiesApi.entityTypeMetadata(namespace, name, true, null)).thenReturn(queryResponse);
    assertThat(rawlsService.entityMetadata(user, id), is(queryResponse));
  }

  @Test
  void entityMetadataException() throws Exception {
    String id = "abc";
    String name = "name";
    String namespace = "namespace";
    WorkspaceResponse response =
        new WorkspaceResponse().workspace(new WorkspaceDetails().name(name).namespace(namespace));
    when(workspacesApi.getWorkspaceById(id, List.of())).thenReturn(response);
    when(entitiesApi.entityTypeMetadata(namespace, name, true, null)).thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.entityMetadata(user, id));
  }
}
