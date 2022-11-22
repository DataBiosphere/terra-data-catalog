package bio.terra.catalog.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StorageSystemTest {
  @Test
  void fromStringIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> StorageSystem.fromString(""));
  }
}
