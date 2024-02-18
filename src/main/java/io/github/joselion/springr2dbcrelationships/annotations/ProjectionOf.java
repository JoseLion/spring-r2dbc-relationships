package io.github.joselion.springr2dbcrelationships.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a class or record to be a projection of another entity. This features
 * usually works out-of-the-box on Spring Boot, but this annotation is required
 * to work with the relational annotations provided by this package.
 *
 * @see <a href="https://docs.spring.io/spring-data/r2dbc/docs/1.4.6/reference/html/#projections">
 *        Projections - Spring R2DBC Reference
 *      </a>
 */
@Inherited
@Documented
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
public @interface ProjectionOf {

  /**
   * The class of the entity to be projected.
   *
   * @return the type of the projection
   */
  Class<?> value();
}
