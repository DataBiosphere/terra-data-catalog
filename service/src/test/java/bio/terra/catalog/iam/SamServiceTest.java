package bio.terra.catalog.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ConfigurationPropertiesScan("bio.terra.catalog")
@ContextConfiguration(classes = {SamService.class})
class SamServiceTest {

  @Autowired private SamService samServiceReal;

  @Mock private UsersApi usersApi;
  @Mock private ResourcesApi resourcesApi;
  @Mock private StatusApi statusApi;

  private static final String USER_TOKEN = "token";
  private static final AuthenticatedUserRequest USER =
      AuthenticatedUserRequest.builder().setToken(USER_TOKEN).setEmail("").setSubjectId("").build();

  private SamService samService;

  @BeforeEach
  void beforeEach() {
    samService = spy(samServiceReal);
    doReturn(usersApi).when(samService).usersApi(USER_TOKEN);
    doReturn(resourcesApi).when(samService).resourcesApi(USER_TOKEN);
    doReturn(statusApi).when(samService).statusApi();
  }

  @Test
  void hasAction() throws Exception {
    var action = SamAction.READ_ANY_METADATA;
    when(resourcesApi.resourceActions(any(), any())).thenReturn(List.of(action.value));
    assertTrue(samService.hasGlobalAction(USER, action));
  }

  @Test
  void getUserStatusInfo() throws Exception {
    UserStatusInfo userStatusInfo = new UserStatusInfo();
    when(usersApi.getUserStatusInfo()).thenReturn(userStatusInfo);
    assertThat(userStatusInfo, is(samService.getUserStatusInfo(USER_TOKEN)));
  }

  @Test
  void status() throws Exception {
    SystemStatus status = new SystemStatus().ok(true);
    when(statusApi.getSystemStatus()).thenReturn(status);
    var samStatus = samService.status();
    assertTrue(samStatus.isOk());
  }

  @Test
  void statusDown() throws Exception {
    SystemStatus status = new SystemStatus().ok(false);
    when(statusApi.getSystemStatus()).thenReturn(status);
    var samStatus = samService.status();
    assertFalse(samStatus.isOk());
  }

  @Test
  void statusException() throws Exception {
    when(statusApi.getSystemStatus()).thenThrow(new ApiException());
    var samStatus = samService.status();
    assertFalse(samStatus.isOk());
  }
}
