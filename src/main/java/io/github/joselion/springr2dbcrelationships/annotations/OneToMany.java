package io.github.joselion.springr2dbcrelationships.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Auditable;
import org.springframework.data.domain.Sort.Direction;

/**
 * Marks a field to have a one-to-many relationship.
 *
 * <p>This annotation also adds the {@link Transient @Transient} and
 * {@link Value @Value("null")} annotations to the field.
 */
@Transient
@Documented
@Value("null") // NOSONAR
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface OneToMany {

  /**
   * Used to specify the name of the "foreing key" column on the child table.
   * This is usually not necessary if the name of the column matches the name
   * of the parent table followed by an {@code _id} suffix.
   *
   * <p>For example, given the parent table is {@code country} and the child
   * table is {@code city}. By default, the annotation will use {@code country_id}
   * as the "foreign key" column of the {@code city} table.
   *
   * @return the name of the "foreing key" column in the child table
   */
  String mappedBy() default "";

  /**
   * Should the entity on the annotated field be readonly. I.e., the entity is
   * never persisted. Defaults to {@code false}.
   *
   * @return whether the annotated entity is readonly or not
   */
  boolean readonly() default false;

  /**
   * The column used to sort the populated children entities. When not
   * specified, the annotation tries to find the field associated to
   * {@link Auditable#getCreatedDate()} or annotated with {@link CreatedDate}.
   * If none can be resolved, falls back to {@code "created_at"} by default.
   * 
   * <p>If all of the above fails, the children will be unsorted.
   *
   * @return the sorting column name
   */
  String sortBy() default "";

  /**
   * The direction to sort the populated children entities. Defaults to
   * {@link Direction#ASC ascending} direction.
   *
   * @return the sort direction
   */
  Direction sortIn() default Direction.ASC;
}
