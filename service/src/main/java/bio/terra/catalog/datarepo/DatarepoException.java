package bio.terra.catalog.datarepo;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.datarepo.client.ApiException;
import org.springframework.http.HttpStatus;

public class DatarepoException extends ErrorReportException {
  public DatarepoException(String message, ApiException e) {
    super(message, e, null, HttpStatus.resolve(e.getCode()));
  }

  public DatarepoException(ApiException e) {
    super(e, HttpStatus.resolve(e.getCode()));
  }
}
