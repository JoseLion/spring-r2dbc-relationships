package io.github.joselion.springr2dbcrelationships.exceptions;

/**
 * Runtime exception thrown when something goes wrong on a relationship process.
 */
public final class RelationshipException extends RuntimeException {

  private RelationshipException(final String message) {
    super(message);
  }

  private RelationshipException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new relationship exception with a detail message.
   *
   * @param message the detail message
   * @return a relationship exception instance
   */
  public static RelationshipException of(final String message) {
    return new RelationshipException(message);
  }

  /**
   * Creates a new relationship exception with the specified cause.
   *
   * @param cause the cause to this exception
   * @return a relationship exception instance
   */
  public static RelationshipException of(final Throwable cause) {
    return new RelationshipException(cause);
  }
}
