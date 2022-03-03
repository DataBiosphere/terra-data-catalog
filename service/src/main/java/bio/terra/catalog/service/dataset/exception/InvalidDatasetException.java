package bio.terra.catalog.service.dataset.exception;

import bio.terra.common.exception.BadRequestException;
import java.util.List;

public class InvalidDatasetException extends BadRequestException {
  public InvalidDatasetException(String message) {
    super(message);
  }
}
