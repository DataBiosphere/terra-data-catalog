package bio.terra.catalog.iam;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.common.iam.BearerTokenParser;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * A Component which always resolves the user email and subjectId from Sam given a request token.
 *
 * <p>This is important for calls made by pet service accounts, which will have a pet email in the
 * request header, but Sam will return the owner's email.
 */
@Component
@SessionScope
public class SamAuthenticatedUserRequestFactory {
  static final String OAUTH2_ACCESS_TOKEN = "OAUTH2_CLAIM_access_token";
  static final String AUTHORIZATION = "Authorization";
  private final SamUserStatusService samService;
  private final HttpServletRequest request;
  private AuthenticatedUserRequest cachedUser;

  @Autowired
  public SamAuthenticatedUserRequestFactory(
      SamUserStatusService samService, HttpServletRequest request) {
    this.samService = samService;
    this.request = request;
  }

  private AuthenticatedUserRequest lookupUser() {
    final var token = getRequiredToken(request);

    // Fetch the user status from Sam
    var userStatusInfo =
        SamRethrow.onInterrupted(() -> samService.getUserStatusInfo(token), "getUserStatusInfo");

    return AuthenticatedUserRequest.builder()
        .setToken(token)
        .setEmail(userStatusInfo.getUserEmail())
        .setSubjectId(userStatusInfo.getUserSubjectId())
        .build();
  }

  public AuthenticatedUserRequest getUser() {
    if (cachedUser == null) {
      cachedUser = lookupUser();
    }
    return cachedUser;
  }

  /**
   * Gets the user token from OAuth2 claim or Authorization header. Throws UnauthorizedException if
   * the token could not be found.
   */
  private String getRequiredToken(HttpServletRequest servletRequest) {
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
