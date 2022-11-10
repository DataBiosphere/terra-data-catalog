package bio.terra.catalog.service.dataset.exception;

import bio.terra.common.exception.ConflictException;

public class InvalidDatasetException extends ConflictException {
  public InvalidDatasetException(String message) {
    super(message);
  }
}
