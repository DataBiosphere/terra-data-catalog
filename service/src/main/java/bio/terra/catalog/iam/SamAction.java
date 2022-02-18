package bio.terra.catalog.iam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum SamAction {
  ALTER_ENTRY("alter_entry");

  private final String name;

  SamAction(String samActionName) {
    this.name = samActionName;
  }

  @JsonCreator
  static SamAction fromValue(String text) {
    return Arrays.stream(values())
        .filter(action -> action.name.equalsIgnoreCase(text))
        .findFirst()
        .orElse(null);
  }
}
