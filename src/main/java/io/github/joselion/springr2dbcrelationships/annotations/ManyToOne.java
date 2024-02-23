package io.github.joselion.springr2dbcrelationships.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.Transient;

/**
 * Marks a field to have a many-to-one relationship.
 *
 * <p>This annotation also adds the {@link Transient @Transient} and
 * {@link Value @Value("null")} annotations to the field.
 */
@Transient
@Documented
@Value("null") // NOSONAR
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface ManyToOne {

  /**
   * Used to specify the name of the "foreing key" column in the current
   * entity's table. This is usually not necessary if the name of the column
   * matches the name of the parent table followed by an {@code _id} suffix.
   *
   * <p>For example, given the parent table is {@code country} and the child
   * table is {@code city}. By default, the annotation will use {@code country_id}
   * as the "foreign key" column of the {@code city} table.
   *
   * @return the name of the "foreing key" column of the entity table
   */
  String foreignKey() default "";

  /**
   * Should the entity on the annotated field be persisted. Defaults to {@code false}.
   *
   * <p>Many-to-one relationships are a back reference of a one-to-many
   * relationship, so they are usually expected to be readonly.
   *
   * @return whether the annotated entity is persisted or not
   */
  boolean persist() default false;
}
