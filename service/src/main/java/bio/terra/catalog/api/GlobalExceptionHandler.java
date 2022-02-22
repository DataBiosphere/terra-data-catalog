package bio.terra.catalog.api;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.datarepo.model.ErrorModel;
import java.util.List;
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
  public ErrorModel samAuthorizationException(UnauthorizedException e) {
    return buildErrorModel(e);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorModel backstopHandler(Exception e) {
    logger.error("Exception caught by backstop handler", e);
    return buildErrorModel(e);
  }

  private ErrorModel buildErrorModel(Throwable ex) {
    return buildErrorModel(ex, null);
  }

  private ErrorModel buildErrorModel(Throwable e, List<String> errorDetail) {
    StringBuilder combinedCauseString = new StringBuilder();
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      combinedCauseString.append("cause: ").append(cause).append(", ");
    }
    logger.error("Global exception handler: " + combinedCauseString, e);
    return new ErrorModel().message(e.getMessage()).errorDetail(errorDetail);
  }
}
