package io.github.joselion.springr2dbcrelationships;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LibraryTest {

  @Test void someLibraryMethodReturnsTrue() {
    final var lib = new Library();

    assertThat(lib.someLibraryMethod()).isTrue();
  }
}
