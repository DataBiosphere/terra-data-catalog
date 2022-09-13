package bio.terra.catalog.service.dataset;

import java.util.UUID;

public record DatasetId(UUID uuid) {
  public String toValue() {
    return uuid.toString();
  }
}
