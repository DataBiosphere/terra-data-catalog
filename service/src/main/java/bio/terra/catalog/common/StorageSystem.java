package bio.terra.catalog.common;

/**
 * The storage systems catalog supports. Every catalog entry has an underlying storage system that
 * handles requests for permission information and other operations.
 */
public enum StorageSystem {
  TERRA_WORKSPACE(bio.terra.catalog.model.StorageSystem.WKS.name()),
  TERRA_DATA_REPO(bio.terra.catalog.model.StorageSystem.TDR.name()),
  EXTERNAL(bio.terra.catalog.model.StorageSystem.EXT.name());

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

  public static StorageSystem fromModel(bio.terra.catalog.model.StorageSystem storageSystem) {
    return fromString(storageSystem.name());
  }

  public bio.terra.catalog.model.StorageSystem toModel() {
    return bio.terra.catalog.model.StorageSystem.valueOf(value);
  }
}
