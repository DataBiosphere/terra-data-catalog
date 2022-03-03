package bio.terra.catalog.service.dataset.exception;

import bio.terra.common.exception.NotFoundException;

public class DatasetNotFoundException extends NotFoundException {
  public DatasetNotFoundException(String message) {
    super(message);
  }
}
