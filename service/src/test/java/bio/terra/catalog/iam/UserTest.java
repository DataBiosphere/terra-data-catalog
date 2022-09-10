package bio.terra.catalog.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.UnauthorizedException;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTest {

  @Mock private HttpServletRequest request;

  private User user;

  @BeforeEach
  void beforeEach() {
    user = new User(request);
  }

  @Test
  void getUser() {
    var token = "token";
    when(request.getHeader(User.OAUTH2_ACCESS_TOKEN)).thenReturn(token);
    assertThat(user.getToken(), is(token));
  }

  @Test
  void getUserNoAuth() {
    assertThrows(UnauthorizedException.class, () -> user.getToken());
  }
}
