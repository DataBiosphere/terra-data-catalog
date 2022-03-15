package bio.terra.catalog.common;

public enum StorageSystem {
  TERRA_WORKSPACE("wks"),
  TERRA_DATA_REPO("tdr"),
  EXTERNAL("ext");

  public final String value;

  StorageSystem(String value) {
    this.value = value;
  }

  public static StorageSystem fromString(String value) {
    for (StorageSystem system : values()) {
      if (system.value.equals(value)) {
        return system;
      }
    }
    throw new IllegalArgumentException("Unknown storage system: " + value);
  }
}
