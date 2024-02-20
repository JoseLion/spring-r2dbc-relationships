package io.github.joselion.springr2dbcrelationships.helpers;

import java.util.UUID;

public final class Constants {

  public static final UUID UUID_ZERO = UUID.fromString("00000000-0000-0000-0000-000000000000");

  private Constants() {
    throw new UnsupportedOperationException("Constants is a helper class");
  }
}
