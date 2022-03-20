package bio.terra.catalog.iam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.SamConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import java.util.List;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SamServiceTest {

  @Mock private ResourcesApi resourcesApi;
  @Mock private StatusApi statusApi;
  @Mock private SamAuthenticatedUserRequestFactory userFactory;

  private SamService samService;

  @BeforeEach
  void beforeEach() {
    samService = spy(new SamService(new SamConfiguration(""), userFactory));
  }

  private void mockResourcesApi() {
    var user = mock(AuthenticatedUserRequest.class);
    when(userFactory.getUser()).thenReturn(user);
    doReturn(resourcesApi).when(samService).resourcesApi(user);
  }

  private void mockStatusApi() {
    doReturn(statusApi).when(samService).statusApi();
  }

  @Test
  void hasAction() throws Exception {
    mockResourcesApi();
    var action = SamAction.READ_ANY_METADATA;
    when(resourcesApi.resourceActions(any(), any())).thenReturn(List.of(action.value));
    assertTrue(samService.hasAction(action));
  }

  @Test
  void status() throws Exception {
    mockStatusApi();
    SystemStatus status = new SystemStatus().ok(true);
    when(statusApi.getSystemStatus()).thenReturn(status);
    var samStatus = samService.status();
    assertTrue(samStatus.isOk());
  }

  @Test
  void statusDown() throws Exception {
    mockStatusApi();
    SystemStatus status = new SystemStatus().ok(false);
    when(statusApi.getSystemStatus()).thenReturn(status);
    var samStatus = samService.status();
    assertFalse(samStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    mockStatusApi();
    when(statusApi.getSystemStatus()).thenThrow(new ApiException());
    var samStatus = samService.status();
    assertFalse(samStatus.isOk());
  }
}
