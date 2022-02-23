package bio.terra.catalog.api;

import bio.terra.catalog.model.ErrorReport;
import bio.terra.common.exception.UnauthorizedException;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(UnauthorizedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorReport samAuthorizationException(UnauthorizedException e) {
    return buildErrorReport(e);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorReport backstopHandler(Exception e) {
    logger.error("Exception caught by backstop handler", e);
    return buildErrorReport(e);
  }

  private ErrorReport buildErrorReport(@Nonnull Throwable e) {
    StringBuilder combinedCauseString = new StringBuilder();
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      combinedCauseString.append("cause: ").append(cause).append(", ");
    }
    logger.error("Global exception handler: " + combinedCauseString, e);
    return new ErrorReport(e.getMessage());
  }
}
