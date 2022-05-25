package scripts.client;

import bio.terra.rawls.client.ApiException;
import bio.terra.rawls.model.WorkspaceDetails;
import bio.terra.rawls.model.WorkspaceRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkspacesApi {
  private static final Logger log = LoggerFactory.getLogger(WorkspacesApi.class);

  private final bio.terra.rawls.api.WorkspacesApi workspacesApi;

  public WorkspacesApi(RawlsClient rawlsClient) {
    workspacesApi = new bio.terra.rawls.api.WorkspacesApi(rawlsClient);
  }

  private RawlsClient getApiClient() {
    return (RawlsClient) workspacesApi.getApiClient();
  }

  public WorkspaceDetails createTestWorkspace() throws ApiException {
    // FIXME: generate namespace (billing account) dynamically, so this test works in all envs.
    var request =
        new WorkspaceRequest()
            .name("catalog_integration_test_" + UUID.randomUUID().toString().replace('-', '_'))
            .namespace("general-dev-billing-account")
            .attributes(Map.of());
    var workspaceDetails = workspacesApi.createWorkspace(request);
    log.info("created workspace " + workspaceDetails.getWorkspaceId());
    return workspaceDetails;
  }

  public void deleteWorkspace(WorkspaceDetails workspaceDetails) throws ApiException {
    try {
      getApiClient().deleteWorkspaceWorkaround = true;
      workspacesApi.deleteWorkspace(workspaceDetails.getNamespace(), workspaceDetails.getName());
    } finally {
      getApiClient().deleteWorkspaceWorkaround = false;
    }
    log.info("deleted workspace " + workspaceDetails.getWorkspaceId());
  }
}
