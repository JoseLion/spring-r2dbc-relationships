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
   * Used to specify the name of the "foreign key" column in the current
   * entity's table. This is usually optional because the anotation can infer
   * the "foreign key" column name in two different ways:
   *
   * <p>1. The name of the column matches the name of the parent table
   * followed by an {@code _id} suffix.
   *
   * <p>2. The name of the column matches the name of the annotated field
   * followed by an {@code _id} suffix.
   *
   * <p>For example, given a parent table {@code country}, a child table
   * {@code city}, and the annotated field {@code @OneToMany Country originCountry;}.
   * By default, the "foreign key" column of the {@code city} table will be
   * inferred as {@code country_id} using option (1). If that fails, it will try
   * {@code origin_country_id} using option (2).
   *
   * @return the name of the "foreign key" column of the entity table
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
