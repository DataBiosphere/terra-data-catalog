package bio.terra.catalog.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class DaoKeyHolder extends GeneratedKeyHolder {

  public UUID getId() {
    return getField("id", UUID.class);
  }

  public Timestamp getTimestamp(String fieldName) {
    return getField(fieldName, Timestamp.class);
  }

  public Instant getCreatedDate() {
    Timestamp timestamp = getTimestamp("created_date");
    if (timestamp != null) {
      return timestamp.toInstant();
    }
    return null;
  }

  public String getString(String fieldName) {
    return getField(fieldName, String.class);
  }

  public <T> T getField(String fieldName, Class<T> type) {
    Map<String, Object> keys = getKeys();
    if (keys != null) {
      Object fieldObject = keys.get(fieldName);
      return type.cast(fieldObject);
    }
    return null;
  }
}
