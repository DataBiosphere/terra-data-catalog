package bio.terra.catalog.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.catalog.config.SamConfiguration;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SamUserStatusServiceTest {

  private static final String USER_TOKEN = "token";

  private SamUserStatusService userStatusService;
  @Mock private UsersApi usersApi;

  @BeforeEach
  void beforeEach() {
    userStatusService = spy(new SamUserStatusService(new SamConfiguration("", "")));
    doReturn(usersApi).when(userStatusService).usersApi(USER_TOKEN);
  }

  @Test
  void getUserStatusInfo() throws Exception {
    UserStatusInfo userStatusInfo = new UserStatusInfo();
    when(usersApi.getUserStatusInfo()).thenReturn(userStatusInfo);
    assertThat(userStatusInfo, is(userStatusService.getUserStatusInfo(USER_TOKEN)));
  }
}
