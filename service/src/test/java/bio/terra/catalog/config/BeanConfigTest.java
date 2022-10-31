package bio.terra.catalog.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.UnauthorizedException;
import javax.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BeanConfigTest {

  @Test
  void bearerToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    BeanConfig beanConfig = new BeanConfig();
    assertThrows(UnauthorizedException.class, () -> beanConfig.bearerToken(request));

    var token = "token";
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    assertThat(beanConfig.bearerToken(request).getToken(), is(token));
  }
}
