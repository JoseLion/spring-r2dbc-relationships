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
 * Marks a field to have a one-to-one relationship.
 *
 * <p>This annotation also adds the {@link Transient @Transient} and
 * {@link Value @Value("null")} annotations to the field.
 */
@Transient
@Documented
@Value("null") // NOSONAR
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface OneToOne {

  /**
   * Whether or not the annotated field is only a backreference in the
   * one-to-one relationship. Defaults to {@code false}.
   *
   * <p>Using the {@code @OneToOne} annotation in the child side instead of the
   * parent is usually intended to have a backreference to the parent.
   * Therefore, when this option is {@code true}, the {@link #readonly()}
   * property is changed to {@code true} by default.
   *
   * @return {@code true} if the field is a backreference, {@code false}
   *         otherwise
   */
  boolean backreference() default false;

  /**
   * Specifies the name of the "foreign key" column in the associated table.
   * This is optional because the annotation can infer the "foreign key" column
   * name in two different ways:
   *
   * <p>1. The column's name matches the parent table's name followed by an
   * {@code _id} suffix.
   *
   * <p>2. The column's name matches the name of the annotated field followed
   * by an {@code _id} suffix.
   * 
   * <p>For example, given a parent table {@code phone}, a child table {@code phone_details},
   * and the annotated field {@code @OneToOne PhoneDetails details;}. The
   * "foreign key" column of the {@code phone_details} table will be inferred
   * as either {@code phone_id} using option (1) or as {@code details_id} using
   * option (2).
   *
   * @return the "foreign key" column name in the associated table
   */
  String mappedBy() default "";

  /**
   * Whether the associated entity are read-only or not, meaning it's never
   * persisted or linked. Defaults to {@code false}.
   *
   * @return {@code true} if the associated entity is read-only, {@code false}
   *         otherwise
   */
  boolean readonly() default false;
}
