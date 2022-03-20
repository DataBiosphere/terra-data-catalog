package bio.terra.catalog.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.UnauthorizedException;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SamAuthenticatedUserRequestFactoryTest {

  @Mock private SamUserStatusService samUserStatusService;
  @Mock private HttpServletRequest request;

  private SamAuthenticatedUserRequestFactory userFactory;

  @BeforeEach
  void beforeEach() {
    userFactory = new SamAuthenticatedUserRequestFactory(samUserStatusService, request);
  }

  @Test
  void getUser() {
    var token = "token";
    when(request.getHeader(SamAuthenticatedUserRequestFactory.OAUTH2_ACCESS_TOKEN))
        .thenReturn(token);
    var email = "email";
    var subject = "subject";
    var userStatus = new UserStatusInfo().userEmail(email).userSubjectId(subject);
    when(samUserStatusService.getUserStatusInfo(token)).thenReturn(userStatus);
    var user = userFactory.getUser();
    assertThat(user.getToken(), is(token));
    assertThat(user.getEmail(), is(email));
    assertThat(user.getSubjectId(), is(subject));
  }

  @Test
  void getUserNoAuth() {
    assertThrows(UnauthorizedException.class, () -> userFactory.getUser());
  }
}
