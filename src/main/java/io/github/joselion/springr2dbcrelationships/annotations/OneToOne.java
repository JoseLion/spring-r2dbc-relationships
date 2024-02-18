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
   * Used to specify the name of the "foreing key" column of the child table.
   * This is usually not necessary if the name of the column matches the name
   * of the parent table followed by an {@code _id} suffix.
   * 
   * <p>For example, given the parent table is {@code phone} and the child
   * table is {@code phone_details}. By default, the annotation will look for
   * the "foreign key" column {@code phone_id} in the {@code phone_details} table.
   *
   * @return the name of the "foreing key" column
   */
  String mappedBy() default "";

  /**
   * Should the entity on the annotated field be readonly. I.e., the entity is
   * never persisted. Defaults to {@code false}.
   *
   * @return whether the annotated entoty is readonly or not
   */
  boolean readonly() default false;
}
