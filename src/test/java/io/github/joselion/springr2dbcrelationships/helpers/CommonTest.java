package io.github.joselion.springr2dbcrelationships.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.joselion.testing.annotations.UnitTest;

@UnitTest class CommonTest {

  @Nested class cast {
    @Nested class when_the_value_can_be_cast {
      @Test void returns_the_value_as_the_parameter_type() {
        final Number value = 3;

        assertThat(Commons.<Integer>cast(value)).isInstanceOf(Integer.class);
      }
    }

    @Nested class when_the_value_cannot_be_cast {
      @Test void throws_a_ClassCastException() {
        assertThatCode(() -> Commons.<Integer>cast("3").intValue()) // NOSONAR
          .isInstanceOf(ClassCastException.class);
      }
    }
  }

  @Nested class capitalize {
    @CsvSource({
      "hello, Hello",
      "world, World",
      "foo, Foo",
      "ah, Ah",
      "x, X",
      "'', ''"
    })
    @ParameterizedTest void capitalizes_the_provided_string(final String text, final String expected) {
      final var result = Commons.capitalize(text);

      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested class uncapitalize {
    @CsvSource({
      "Hello, hello",
      "World, world",
      "Foo, foo",
      "Ah, ah",
      "X, x",
      "'', ''"
    })
    @ParameterizedTest void uncapitalizes_the_provided_string(final String text, final String expected) {
      final var result = Commons.uncapitalize(text);

      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested class toSnakeCase {
    @CsvSource({
      "someLongText, some_long_text",
      "helloWorld, hello_world",
      "getABC, get_abc",
      "HELLO, hello",
      "'', ''"
    })
    @ParameterizedTest void transforms_the_string_to_snake_case(final String text, final String expected) {
      final var result = Commons.toSnakeCase(text);

      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested class toCamelCase {
    @CsvSource({
      "some_long_text, someLongText",
      "hello_world, helloWorld",
      "get_abc, getAbc",
      "hello, hello",
      "'', ''"
    })
    @ParameterizedTest void transforms_the_string_to_camel_case(final String text, final String expected) {
      final var result = Commons.toCamelCase(text);

      assertThat(result).isEqualTo(expected);
    }
  }
}
