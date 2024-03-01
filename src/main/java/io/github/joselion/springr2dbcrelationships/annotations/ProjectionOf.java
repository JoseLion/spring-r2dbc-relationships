package io.github.joselion.springr2dbcrelationships.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a type as a projection of an entity. This feature works out of the box
 * on Spring Boot, but the annotation is needed for relationship processors to
 * obtain the accurate entity information hidden in the projection.
 *
 * @see <a href="https://docs.spring.io/spring-data/relational/reference/repositories/projections.html">
 *        Spring Data Relational | Projections
 *      </a>
 */
@Inherited
@Documented
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
public @interface ProjectionOf {

  /**
   * The the entity type of the projection.
   *
   * @return the entity type
   */
  Class<?> value();
}
