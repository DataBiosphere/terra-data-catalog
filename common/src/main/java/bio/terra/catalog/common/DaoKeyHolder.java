package bio.terra.catalog.common;

import bio.terra.catalog.service.dataset.DatasetId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class DaoKeyHolder extends GeneratedKeyHolder {

  public DatasetId getId() {
    return new DatasetId(getField("id", UUID.class));
  }

  public Timestamp getTimestamp(String fieldName) {
    return getField(fieldName, Timestamp.class);
  }

  public Instant getCreatedDate() {
    Timestamp timestamp = getTimestamp("created_date");
    return timestamp.toInstant();
  }

  public String getString(String fieldName) {
    return getField(fieldName, String.class);
  }

  public <T> T getField(String fieldName, Class<T> type) {
    // In practice getKeys() will never return null
    Object fieldObject = Objects.requireNonNull(getKeys()).get(fieldName);
    return type.cast(fieldObject);
  }
}
