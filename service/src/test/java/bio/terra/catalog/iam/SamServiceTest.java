package bio.terra.catalog.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.SamConfiguration;
import bio.terra.common.iam.AuthenticatedUserRequest;
import java.util.List;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SamServiceTest {

  @Mock private SamClient samClient;
  @Mock private UsersApi usersApi;
  @Mock private ResourcesApi resourcesApi;
  @Mock private StatusApi statusApi;

  private static final String TOKEN = "token";
  private static final AuthenticatedUserRequest USER =
      AuthenticatedUserRequest.builder().setToken(TOKEN).setEmail("").setSubjectId("").build();

  private SamService samService;

  @BeforeEach
  void beforeEach() {
    samService = new SamService(new SamConfiguration("", ""), samClient);
  }

  private void mockUsers() {
    when(samClient.usersApi(TOKEN)).thenReturn(usersApi);
  }

  private void mockResources() {
    when(samClient.resourcesApi(TOKEN)).thenReturn(resourcesApi);
  }

  private void mockStatus() {
    when(samClient.statusApi()).thenReturn(statusApi);
  }

  @Test
  void hasAction() throws Exception {
    mockResources();
    var action = SamAction.READ_ANY_METADATA;
    when(resourcesApi.resourceActionsV2(any(), any())).thenReturn(List.of(action.value));
    assertTrue(samService.hasGlobalAction(USER, action));
  }

  @Test
  void getUserStatusInfo() throws Exception {
    mockUsers();
    UserStatusInfo userStatusInfo = new UserStatusInfo();
    when(usersApi.getUserStatusInfo()).thenReturn(userStatusInfo);
    assertThat(userStatusInfo, is(samService.getUserStatusInfo(TOKEN)));
  }

  @Test
  void status() throws Exception {
    mockStatus();
    SystemStatus status = new SystemStatus().ok(true);
    when(statusApi.getSystemStatus()).thenReturn(status);
    var samStatus = samService.status();
    assertTrue(samStatus.isOk());
  }

  @Test
  void statusDown() throws Exception {
    mockStatus();
    SystemStatus status = new SystemStatus().ok(false);
    when(statusApi.getSystemStatus()).thenReturn(status);
    var samStatus = samService.status();
    assertFalse(samStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    mockStatus();
    when(statusApi.getSystemStatus()).thenThrow(new ApiException());
    var samStatus = samService.status();
    assertFalse(samStatus.isOk());
  }
}
