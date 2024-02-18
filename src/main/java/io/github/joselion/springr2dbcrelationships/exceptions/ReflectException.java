package io.github.joselion.springr2dbcrelationships.exceptions;

import io.github.joselion.springr2dbcrelationships.helpers.Reflect;

/**
 * Runtime exception thrown when something goes wrong with a {@link Reflect}
 * helper process.
 */
public final class ReflectException extends RuntimeException {

  private ReflectException(final String message) {
    super(message);
  }

  private ReflectException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a reflect exception with a detail message.
   *
   * @param message the detail message
   * @return a reflect exception instance
   */
  public static ReflectException of(final String message) {
    return new ReflectException(message);
  }

  /**
   * Creates a reflect exception with the specified cause.
   *
   * @param cause the cause to this exception
   * @return a reflect exception instance
   */
  public static ReflectException of(final Throwable cause) {
    return new ReflectException(cause);
  }
}
