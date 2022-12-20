package bio.terra.catalog.rawls;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.rawls.client.ApiException;
import org.springframework.http.HttpStatus;

public class RawlsException extends ErrorReportException {
  public RawlsException(String message, ApiException e) {
    super(message, e, null, HttpStatus.resolve(e.getCode()));
  }
}
