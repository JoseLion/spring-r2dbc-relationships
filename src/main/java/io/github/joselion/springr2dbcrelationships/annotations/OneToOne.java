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
   * Set to {@code true} if the annotated field is only a back reference in the
   * child entity of the one-to-one relationship. This is {@code false} by
   * default as the annotaton is mostly used o the parent side of the
   * relationship.
   *
   * <p>Using the {@code @OneToOne} annotation in the child side instead of the
   * parent is ussually just with the intention of having a back references to
   * the parent. Therefore, when this property is set to {@code true} the
   * {@link #readonly()} property is true {@code true} as well.
   *
   * @return whether he annotation is used as a back reference or not.
   */
  boolean backReference() default false;

  /**
   * Used to specify the name of the "foreign key" column in the child table.
   * This is usually optional because the anotation can infer the "foreign key"
   * column name in two different ways:
   *
   * <p>1. The name of the column matches the name of the parent table
   * followed by an {@code _id} suffix.
   *
   * <p>2. The name of the column matches the name of the annotated field
   * followed by an {@code _id} suffix.
   * 
   * <p>For example, given a parent table {@code phone}, a child table
   * {@code phone_details}, and the annotated field {@code @OneToOne PhoneDetails details;}.
   * By default, the "foreign key" column of the {@code phone_details} table
   * will be inferred as {@code phone_id} using option (1). If that fails, it
   * will try {@code details_id} using option (2).
   *
   * @return the name of the "foreign key" column
   */
  String mappedBy() default "";

  /**
   * Whether the associated entity is read-only or not, meaning it's never
   * persisted or linked. Defaults to {@code false}.
   *
   * @return {@code true} if the associated entity is read-only, {@code false}
   *         otherwise
   */
  boolean readonly() default false;
}
