package bio.terra.catalog.rawls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.rawls.api.EntitiesApi;
import bio.terra.rawls.api.StatusApi;
import bio.terra.rawls.api.WorkspacesApi;
import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.EntityCopyResponse;
import bio.terra.rawls.model.EntityQueryResponse;
import bio.terra.rawls.model.EntityTypeMetadata;
import bio.terra.rawls.model.WorkspaceAccessLevel;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceListResponse;
import bio.terra.rawls.model.WorkspaceName;
import bio.terra.rawls.model.WorkspaceResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RawlsServiceTest {
  private RawlsService rawlsService;

  @Mock private RawlsClient rawlsClient;
  @Mock private AuthenticatedUserRequest user;

  @Mock private EntitiesApi entitiesApi;

  @Mock private StatusApi statusApi;
  @Mock private WorkspacesApi workspacesApi;

  @BeforeEach
  void beforeEach() {
    rawlsService = new RawlsService(rawlsClient);
  }

  private void mockWorkspaces() {
    when(rawlsClient.workspacesApi(user)).thenReturn(workspacesApi);
  }

  private void mockEntities() {
    when(rawlsClient.entitiesApi(user)).thenReturn(entitiesApi);
  }

  private void mockStatus() {
    when(rawlsClient.statusApi()).thenReturn(statusApi);
  }

  @Test
  void status() {
    mockStatus();
    var rawlsStatus = rawlsService.status();
    assertTrue(rawlsStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    mockStatus();
    doThrow(new ApiException()).when(statusApi).systemStatus();
    var rawlsStatus = rawlsService.status();
    assertFalse(rawlsStatus.isOk());
  }

  @Test
  void getWorkspaces() throws Exception {
    mockWorkspaces();
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
  void getWorkspacesException() throws Exception {
    mockWorkspaces();
    when(workspacesApi.listWorkspaces(RawlsService.ACCESS_LEVEL_AND_ID))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.getWorkspaceIdsAndRoles(user));
  }

  @Test
  void getRoleReader() throws Exception {
    mockWorkspaces();
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.READER));
    assertThat(rawlsService.getRole(user, id), is(DatasetAccessLevel.READER));
  }

  @Test
  void getRoleOwner() throws Exception {
    mockWorkspaces();
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenReturn(new WorkspaceResponse().accessLevel(WorkspaceAccessLevel.OWNER));
    assertThat(rawlsService.getRole(user, id), is(DatasetAccessLevel.OWNER));
  }

  @Test
  void userHasActionException() throws Exception {
    mockWorkspaces();
    String id = "abc";
    when(workspacesApi.getWorkspaceById(id, RawlsService.ACCESS_LEVEL))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.getRole(user, id));
  }

  @Test
  void entityQuery() throws Exception {
    mockWorkspaces();
    mockEntities();
    String id = "abc";
    String name = "name";
    String namespace = "namespace";
    String tableName = "table";
    WorkspaceResponse response =
        new WorkspaceResponse().workspace(new WorkspaceDetails().name(name).namespace(namespace));
    when(workspacesApi.getWorkspaceById(id, List.of())).thenReturn(response);
    EntityQueryResponse queryResponse = new EntityQueryResponse();
    when(entitiesApi.entityQuery(
            namespace, name, tableName, null, null, null, null, null, null, List.of(), null, null))
        .thenReturn(queryResponse);
    assertThat(rawlsService.entityQuery(user, id, tableName, 10), is(queryResponse));
  }

  @Test
  void entityQueryException() throws Exception {
    mockWorkspaces();
    mockEntities();

    String id = "abc";
    String name = "name";
    String namespace = "namespace";
    String tableName = "table";
    WorkspaceResponse response =
        new WorkspaceResponse().workspace(new WorkspaceDetails().name(name).namespace(namespace));
    when(workspacesApi.getWorkspaceById(id, List.of())).thenReturn(response);
    when(entitiesApi.entityQuery(
            namespace, name, tableName, null, null, null, null, null, null, List.of(), null, null))
        .thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.entityQuery(user, id, tableName, 10));
  }

  @Test
  void entityMetadata() throws Exception {
    mockWorkspaces();
    mockEntities();
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
    mockWorkspaces();
    mockEntities();
    String id = "abc";
    String name = "name";
    String namespace = "namespace";
    WorkspaceResponse response =
        new WorkspaceResponse().workspace(new WorkspaceDetails().name(name).namespace(namespace));
    when(workspacesApi.getWorkspaceById(id, List.of())).thenReturn(response);
    when(entitiesApi.entityTypeMetadata(namespace, name, true, null)).thenThrow(new ApiException());
    assertThrows(RawlsException.class, () -> rawlsService.entityMetadata(user, id));
  }

  @Test
  void getWorkspaceName() {
    String namespace = "hello";
    String name = "world";
    WorkspaceDetails workspaceDetails = new WorkspaceDetails().namespace(namespace).name(name);
    WorkspaceName workspaceName = RawlsService.getWorkspaceName(workspaceDetails);
    assertThat(workspaceDetails.getNamespace(), is(namespace));
    assertThat(workspaceName.getName(), is(name));
  }

  @Test
  void getExportWorkspace() throws ApiException {
    mockWorkspaces();
    mockEntities();

    String workspaceIdSource = "workspaceSource";
    String workspaceIdDest = "workspaceDest";

    var workspaceResponseSource = mock(WorkspaceResponse.class);
    when(workspacesApi.getWorkspaceById(workspaceIdSource, List.of()))
        .thenReturn(workspaceResponseSource);
    when(workspaceResponseSource.getWorkspace())
        .thenReturn(new WorkspaceDetails().namespace("namespaceSource").name("nameSource"));

    var workspaceResponseDest = mock(WorkspaceResponse.class);
    when(workspacesApi.getWorkspaceById(workspaceIdDest, List.of()))
        .thenReturn(workspaceResponseDest);
    when(workspaceResponseDest.getWorkspace())
        .thenReturn(new WorkspaceDetails().namespace("namespaceDest").name("nameDest"));

    var entityCopyResponse = mock(EntityCopyResponse.class);
    when(entitiesApi.copyEntities(any(), any())).thenReturn(entityCopyResponse);

    rawlsService.exportWorkspaceDataset(user, workspaceIdSource, workspaceIdDest);
    verify(entitiesApi).copyEntities(any(), any());
  }

  @Test
  void getExportWorkspaceException() throws ApiException {
    mockWorkspaces();

    String workspaceIdSource = "workspaceSource";
    String workspaceIdDest = "workspaceDest";
    when(workspacesApi.getWorkspaceById(workspaceIdSource, List.of()))
        .thenThrow(new ApiException());
    assertThrows(
        RawlsException.class,
        () -> rawlsService.exportWorkspaceDataset(user, workspaceIdSource, workspaceIdDest));
  }
}
