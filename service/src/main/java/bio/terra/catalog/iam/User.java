package bio.terra.catalog.iam;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenParser;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * This represents the user associated with the current HTTP request. If injected into a singleton
 * scope class, such as a service, spring will create a proxy object so the value for the current
 * http request is returned. If no http scope exists when this is used, the proxy will throw an
 * exception.
 */
@Component
@RequestScope
public class User {
  static final String OAUTH2_ACCESS_TOKEN = "OAUTH2_CLAIM_access_token";
  static final String AUTHORIZATION = "Authorization";

  private final HttpServletRequest httpRequest;

  @Autowired
  public User(HttpServletRequest httpRequest) {
    this.httpRequest = httpRequest;
  }

  /**
   * @return the current user's authentication token.
   * @throws UnauthorizedException if no authentication is found for the current user
   */
  public String getToken() {
    String oauth2Header = httpRequest.getHeader(OAUTH2_ACCESS_TOKEN);
    if (oauth2Header != null) {
      return oauth2Header;
    }
    String authHeader = httpRequest.getHeader(AUTHORIZATION);
    if (authHeader != null) {
      // Invalid tokens will throw an UnauthorizedException.
      return BearerTokenParser.parse(authHeader);
    }
    throw new UnauthorizedException("Unable to retrieve access token");
  }
}
