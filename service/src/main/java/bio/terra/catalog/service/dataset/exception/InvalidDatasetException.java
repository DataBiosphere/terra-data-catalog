package bio.terra.catalog.service.dataset.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidDatasetException extends BadRequestException {
  public InvalidDatasetException(String message, Throwable cause) {
    super(message, cause);
  }
}
