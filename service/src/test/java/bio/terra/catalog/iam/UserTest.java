package bio.terra.catalog.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class UserTest {

  private final MockHttpServletRequest request = new MockHttpServletRequest();

  private final User user = new User(request);

  @Test
  void getTokenOauth2() {
    var token = "token";
    request.addHeader(User.OAUTH2_ACCESS_TOKEN, token);
    assertThat(user.getToken(), is(token));
  }

  @Test
  void getTokenBearer() {
    var token = "token";
    request.addHeader(User.AUTHORIZATION, "Bearer " + token);
    assertThat(user.getToken(), is(token));
  }

  @Test
  void geTokenNoAuth() {
    assertThrows(UnauthorizedException.class, user::getToken);

    request.addHeader(User.AUTHORIZATION, "bogus");
    assertThrows(UnauthorizedException.class, user::getToken);
  }
}
