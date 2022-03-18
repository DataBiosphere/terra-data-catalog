package bio.terra.catalog.iam;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum SamAction {
  CREATE_METADATA("create_metadata"),
  READ_ANY_METADATA("read_any_metadata"),
  UPDATE_ANY_METADATA("update_any_metadata"),
  DELETE_ANY_METADATA("delete_any_metadata");

  public final String name;

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
