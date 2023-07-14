package bio.terra.catalog.service.dataset.exception;

import bio.terra.common.exception.NotFoundException;

// Disable sonar warning: Inheritance tree of classes should not be too deep
@SuppressWarnings("java:S110")
public class DatasetNotFoundException extends NotFoundException {

  public DatasetNotFoundException(String message) {
    super(message);
  }

  public DatasetNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
