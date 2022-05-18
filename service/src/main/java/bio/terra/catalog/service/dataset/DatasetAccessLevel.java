package bio.terra.catalog.service.dataset;

import bio.terra.catalog.iam.SamAction;
import java.util.EnumSet;
import java.util.Set;

public enum DatasetAccessLevel {
  OWNER("owner", EnumSet.allOf(SamAction.class)),
  READER("reader", EnumSet.of(SamAction.READ_ANY_METADATA)),
  DISCOVERER("discoverer", Set.of());

  private final String name;
  private final Set<SamAction> allowedActions;

  DatasetAccessLevel(String name, Set<SamAction> allowedActions) {
    this.name = name;
    this.allowedActions = allowedActions;
  }

  @Override
  public String toString() {
    return name;
  }

  public boolean hasAction(SamAction action) {
    return this.allowedActions.contains(action);
  }

  public static DatasetAccessLevel fromValue(String input) {
    for (DatasetAccessLevel b : DatasetAccessLevel.values()) {
      if (b.name.equals(input)) {
        return b;
      }
    }
    return null;
  }
}
