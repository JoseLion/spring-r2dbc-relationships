package io.github.joselion.springr2dbcrelationships.helpers;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

/**
 * Common helpers.
 */
public final class Commons {

  private Commons() {
    throw new UnsupportedOperationException("Common is a helper class");
  }

  /**
   * Convenience method to cast with a generic type.
   *
   * @param <T> the type to cast the target to
   * @param target the target to cast
   * @return a value cast to the generic type
   */
  @SuppressWarnings("unchecked")
  public static <T> T cast(final Object target) {
    return (T) target;
  }

  /**
   * Capitalizes the first character of the provided string.
   *
   * @param value the string to capitalize
   * @return the capitalized string
   */
  public static String capitalize(final String value) {
    if (!value.isEmpty()) {
      final var first = value.substring(0, 1);
      final var rest = value.substring(1);

      return first.toUpperCase().concat(rest);
    }

    return value;
  }

  /**
   * Uncapitalizes the first character of the provided string.
   *
   * @param value the string to uncapitalize
   * @return the uncapitalized string
   */
  public static String uncapitalize(final String value) {
    if (!value.isEmpty()) {
      final var first = value.substring(0, 1);
      final var rest = value.substring(1);

      return first.toLowerCase().concat(rest);
    }

    return value;
  }

  /**
   * Transforms a string to snake-case format.
   *
   * @param value the string to transform
   * @return the text in snake-case format
   */
  public static String toSnakeCase(final String value) {
    return value
      .replaceAll("([a-z])([A-Z]+)", "$1_$2")
      .toLowerCase();
  }

  /**
   * Transforms a string to camel-case format.
   *
   * @param value the string to transform
   * @return the text in camel-case format
   */
  public static String toCamelCase(final String value) {
    final var words = value.split("[\\W_]+");
    final var joined = stream(words)
      .map(Commons::capitalize)
      .collect(joining());

    return Commons.uncapitalize(joined);
  }
}
