package bio.terra.catalog.iam;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenParser;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * A Component that extracts the bearer token for the current HTTP request, if any..
 *
 * <p>If the current user isn't authenticated, trying to get their token will throw an {@link
 * UnauthorizedException}.
 */
@Component
@RequestScope
public class User {
  static final String OAUTH2_ACCESS_TOKEN = "OAUTH2_CLAIM_access_token";
  static final String AUTHORIZATION = "Authorization";

  private final HttpServletRequest servletRequest;

  @Autowired
  public User(HttpServletRequest servletRequest) {
    this.servletRequest = servletRequest;
  }

  /**
   * Get the user token from OAuth2 claim or Authorization header. Throws UnauthorizedException if
   * the token could not be found.
   */
  public String getToken() {
    String oauth2Header = servletRequest.getHeader(OAUTH2_ACCESS_TOKEN);
    if (oauth2Header != null) {
      return oauth2Header;
    } else {
      String authHeader = servletRequest.getHeader(AUTHORIZATION);
      if (authHeader != null) {
        return BearerTokenParser.parse(authHeader);
      }
    }
    throw new UnauthorizedException("Unable to retrieve access token");
  }
}
